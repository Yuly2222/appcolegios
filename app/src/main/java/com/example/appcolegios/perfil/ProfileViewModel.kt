package com.example.appcolegios.perfil

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.content.ContentResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.storage.StorageReference
import com.google.firebase.FirebaseApp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns

// Modelo simple para perfil de docente
data class TeacherProfile(
    val nombre: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null
)

class ProfileViewModel : ViewModel() {
    companion object {
        private const val TAG = "ProfileViewModel"
    }
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = run {
        try {
            val configuredBucket = FirebaseApp.getInstance().options.storageBucket
            val bucketUrl = if (!configuredBucket.isNullOrBlank()) {
                if (configuredBucket.startsWith("gs://")) configuredBucket else "gs://$configuredBucket"
            } else null
            if (!bucketUrl.isNullOrBlank()) {
                try {
                    FirebaseStorage.getInstance(FirebaseApp.getInstance(), bucketUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not initialize FirebaseStorage with bucketUrl=$bucketUrl, falling back to default", e)
                    FirebaseStorage.getInstance()
                }
            } else {
                FirebaseStorage.getInstance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading FirebaseApp storageBucket, falling back to default storage", e)
            FirebaseStorage.getInstance()
        }
    }

    // Devuelve una lista de StorageReference candidatas para un path dado.
    private fun candidateRefs(path: String): List<StorageReference> {
        val refs = mutableListOf<StorageReference>()
        try {
            refs.add(storage.reference.child(path))
        } catch (_: Exception) { }

        try {
            val configuredBucket = FirebaseApp.getInstance().options.storageBucket
            if (!configuredBucket.isNullOrBlank()) {
                var bucket = configuredBucket
                if (bucket.startsWith("gs://")) bucket = bucket.removePrefix("gs://")
                val appspot = if (bucket.contains("firebasestorage.app")) bucket.replace("firebasestorage.app", "appspot.com") else bucket
                val candidates = listOf(bucket, appspot)
                for (b in candidates.distinct()) {
                    try {
                        val url = if (b.startsWith("gs://")) b else "gs://$b"
                        val ref = FirebaseStorage.getInstance().getReferenceFromUrl(url).child(path)
                        refs.add(ref)
                    } catch (_: Exception) { }
                    try {
                        val httpsBucket = if (b.startsWith("gs://")) b.removePrefix("gs://") else b
                        val httpsUrl = "https://firebasestorage.googleapis.com/v0/b/$httpsBucket"
                        val ref2 = FirebaseStorage.getInstance().getReferenceFromUrl(httpsUrl).child(path)
                        refs.add(ref2)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        return refs.distinctBy { it.path }
    }

    private val _student = MutableStateFlow<Result<Student?>?>(null)
    val student: StateFlow<Result<Student?>?> = _student

    // Estado para profesor
    private val _teacherState = MutableStateFlow<Result<TeacherProfile?>?>(null)
    val teacherState: StateFlow<Result<TeacherProfile?>?> = _teacherState

    // Lista de hijos (para padres): se llena buscando students donde acudienteId == currentUid
    private val _children = MutableStateFlow<List<Student>>(emptyList())
    @Suppress("unused")
    val children: StateFlow<List<Student>> = _children

    // Rol del usuario cargado desde Firestore (users y colecciones específicas)
    private val _roleString = MutableStateFlow<String?>(null)
    val roleString: StateFlow<String?> = _roleString

    init {
        loadStudentData()
        loadTeacherData()
        loadChildrenForParent()
        loadRoleFromDb()
    }

    private fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val studentDoc = db.collection("students").document(userId).get().await()
                    if (studentDoc.exists()) {
                        Log.d(TAG, "loadStudentData: found students/$userId")
                        val studentData = studentDoc.toObject(Student::class.java) ?: Student(id = userId)
                        // Intentar leer users/{uid} para completar/actualizar curso/grupo si existen allí
                        try {
                            val userDoc = db.collection("users").document(userId).get().await()
                            if (userDoc.exists()) {
                                val cursoFromUser = userDoc.getString("curso") ?: userDoc.getString("course")
                                val grupoFromUser = userDoc.getString("grupo") ?: userDoc.getString("group")
                                // Si alguno de los campos viene en users, los aplicamos sobre studentData
                                val merged = studentData.copy(
                                    curso = cursoFromUser ?: studentData.curso,
                                    grupo = grupoFromUser ?: studentData.grupo
                                )
                                Log.d(TAG, "loadStudentData: merged Student from students/$userId + users/$userId -> $merged")
                                _student.value = Result.success(merged)
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "loadStudentData: error reading users/$userId while merging", e)
                        }
                        _student.value = Result.success(studentData)
                    } else {
                        Log.d(TAG, "loadStudentData: students/$userId not found, trying users/$userId")
                        // Intentar leer desde users/{uid} cuando no exista students/{uid}
                        try {
                            val userDoc = db.collection("users").document(userId).get().await()
                            if (userDoc.exists()) {
                                Log.d(TAG, "loadStudentData: found users/$userId, fields: name=${userDoc.getString("name")}, curso=${userDoc.getString("curso")}, grupo=${userDoc.getString("grupo")}, avatarUrlExists=${userDoc.getString("avatarUrl") != null || userDoc.getString("avatarBase64") != null}")
                                val name = userDoc.getString("name") ?: userDoc.getString("displayName") ?: ""
                                val curso = userDoc.getString("curso") ?: userDoc.getString("course") ?: ""
                                val grupo = userDoc.getString("grupo") ?: userDoc.getString("group") ?: ""
                                // Normalizar avatar: preferir avatarUrl/photoUrl/avatar; si solo hay base64 crudo convertir a data URL
                                val rawAvatarUrl = userDoc.getString("avatarUrl") ?: userDoc.getString("photoUrl") ?: userDoc.getString("avatar")
                                val avatarBase64 = userDoc.getString("avatarBase64")
                                val avatar = when {
                                    !rawAvatarUrl.isNullOrBlank() -> rawAvatarUrl
                                    !avatarBase64.isNullOrBlank() -> {
                                        // si ya incluye prefijo data: lo dejamos, si no lo normalizamos a data:image/jpeg
                                        if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                    }
                                    else -> null
                                }
                                val promedio = try { (userDoc.getDouble("promedio") ?: userDoc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                                val mapped = Student(
                                    id = userId,
                                    nombre = name,
                                    curso = curso,
                                    grupo = grupo,
                                    promedio = promedio,
                                    avatarUrl = avatar
                                )
                                Log.d(TAG, "loadStudentData: mapped Student from users/$userId -> $mapped")
                                _student.value = Result.success(mapped)
                            } else {
                                Log.d(TAG, "loadStudentData: users/$userId not found either, returning empty Student")
                                // Ningún documento encontrado: devolver Student vacío
                                _student.value = Result.success(Student(id = userId))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "loadStudentData: error reading users/$userId", e)
                            _student.value = Result.failure(e)
                        }
                    }
                } catch (e: Exception) {
                    _student.value = Result.failure(e)
                }
            }
        }
    }

    private fun loadTeacherData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _teacherState.value = Result.success(null)
                return@launch
            }
            try {
                val doc = db.collection("teachers").document(userId).get().await()
                if (doc.exists()) {
                    val nombre = doc.getString("name") ?: doc.getString("displayName")
                    val email = doc.getString("email") ?: auth.currentUser?.email
                    val phone = doc.getString("phone")
                    val photoBase64 = doc.getString("photoBase64")
                    val photo = if (!photoBase64.isNullOrBlank()) {
                        "data:image/jpeg;base64,$photoBase64"
                    } else {
                        doc.getString("photoUrl") ?: doc.getString("avatar")
                    }
                    _teacherState.value = Result.success(TeacherProfile(nombre, email, phone, photo))
                    return@launch
                }

                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    val nombre = userDoc.getString("displayName") ?: userDoc.getString("name")
                    val email = userDoc.getString("email") ?: auth.currentUser?.email
                    val phone = userDoc.getString("phone")
                    val photoBase64 = userDoc.getString("photoBase64")
                    val photo = if (!photoBase64.isNullOrBlank()) {
                        "data:image/jpeg;base64,$photoBase64"
                    } else {
                        userDoc.getString("photoUrl") ?: userDoc.getString("avatar")
                    }
                    _teacherState.value = Result.success(TeacherProfile(nombre, email, phone, photo))
                    return@launch
                }

                _teacherState.value = Result.success(null)
            } catch (_: Exception) {
                _teacherState.value = Result.failure(Exception("Error al cargar perfil docente"))
            }
        }
    }

    // Cargar hijos asociados al padre actual. Busca en 'students' por 'acudienteId' y por 'acudienteEmail' como fallback.
    private fun loadChildrenForParent() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            val userEmail = auth.currentUser?.email
            if (userId == null && userEmail == null) return@launch
            try {
                val childrenList = mutableListOf<Student>()
                // Buscar por acudienteId
                val byIdQuery = db.collection("students").whereEqualTo("acudienteId", userId).get().await()
                for (doc in byIdQuery.documents) {
                    val s = doc.toObject(Student::class.java)
                    if (s != null) childrenList.add(s)
                }
                // Si no encontró nada, intentar por email
                if (childrenList.isEmpty() && !userEmail.isNullOrBlank()) {
                    val byEmailQuery = db.collection("students").whereEqualTo("acudienteEmail", userEmail).get().await()
                    for (doc in byEmailQuery.documents) {
                        val s = doc.toObject(Student::class.java)
                        if (s != null) childrenList.add(s)
                    }
                }
                // Actualizar estado
                _children.value = childrenList

                // Si hay hijos, establecer el primero como student seleccionado por defecto (para mantener compatibilidad con StudentCard)
                if (childrenList.isNotEmpty()) {
                    _student.value = Result.success(childrenList[0])
                }
            } catch (e: Exception) {
                // En caso de error solo dejamos la lista vacía
                _children.value = emptyList()
            }
        }
    }

    // Guardar/actualizar perfil del docente en Firestore
    fun saveTeacherProfile(name: String?, phone: String?, photoUrl: String?) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                val map = hashMapOf<String, Any?>(
                    "name" to name,
                    "displayName" to name,
                    "phone" to phone,
                    "photoUrl" to photoUrl
                )
                // Guardar en 'teachers' y en 'users' para coherencia
                db.collection("teachers").document(userId).set(map).await()
                try {
                    db.collection("users").document(userId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (_: Exception) { }
                // Actualizar estado local
                _teacherState.value = Result.success(TeacherProfile(name, auth.currentUser?.email, phone, photoUrl))
            } catch (e: Exception) {
                _teacherState.value = Result.failure(e)
            }
        }
    }

    // Subir foto a Firebase Storage y devolver URL y mensaje de error (si ocurre)
    @Suppress("unused")
    fun uploadPhoto(uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }
            try {
                val path = "avatars/$userId.jpg"
                val refs = candidateRefs(path)
                try {
                    var downloadUrl: String? = null
                    var succeeded = false
                    for (r in refs) {
                        try {
                            val snap = r.putFile(uri).await()
                            downloadUrl = snap.storage.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (_: Exception) {
                        }
                    }
                    if (!succeeded) throw Exception("All candidate refs failed")
                    saveTeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, _teacherState.value?.getOrNull()?.phone, downloadUrl)
                    onResult(downloadUrl, null)
                } catch (e: Exception) {
                    try {
                        val configuredBucket = FirebaseApp.getInstance().options.storageBucket
                        if (!configuredBucket.isNullOrBlank()) {
                            var altBucket = configuredBucket
                            if (altBucket.contains("firebasestorage.app")) altBucket = altBucket.replace("firebasestorage.app", "appspot.com")
                            val altUrl = if (altBucket.startsWith("gs://")) altBucket else "gs://$altBucket"
                            val altRef = FirebaseStorage.getInstance().getReferenceFromUrl(altUrl).child("avatars/$userId.jpg")
                            val snapshot2 = altRef.putFile(uri).await()
                            val download2 = snapshot2.storage.downloadUrl.await().toString()
                            saveTeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, _teacherState.value?.getOrNull()?.phone, download2)
                            onResult(download2, null)
                            return@launch
                        }
                    } catch (_: Exception) {
                        onResult(null, e.message ?: e.toString())
                        return@launch
                    }
                    onResult(null, e.message ?: e.toString())
                }
            } catch (_: Exception) {
                onResult(null, "Error desconocido al iniciar la subida")
            }
        }
    }

    // Variante que acepta un ContentResolver y hace un intento por putFile, y si falla, usa putStream.
    fun uploadPhotoWithResolver(contentResolver: ContentResolver, uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }
            val path = "avatars/$userId.jpg"
            val refs = candidateRefs(path)
            // Primero intentamos putFile
            try {
                try {
                    var succeeded = false
                    var download: String? = null
                    for (r in refs) {
                        try {
                            r.putFile(uri).await()
                            download = r.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "putFile (with resolver) to candidate ref failed, trying next", e)
                        }
                    }
                    if (!succeeded || download == null) throw Exception("All candidate refs failed")
                    saveTeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, _teacherState.value?.getOrNull()?.phone, download)
                    onResult(download, null)
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "putFile failed, trying putStream", e)
                }

                // Intentar con stream (útil cuando el URI requiere permisos especiales)
                val input = try { contentResolver.openInputStream(uri) } catch (e: Exception) { null }
                if (input == null) {
                    onResult(null, "No se pudo abrir el archivo para subir")
                    return@launch
                }
                try {
                    var succeeded = false
                    var download: String? = null
                    for (r in refs) {
                        try {
                            r.putStream(input).await()
                            download = r.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "putStream to candidate ref failed, trying next", e)
                        }
                    }
                    if (!succeeded || download == null) throw Exception("All candidate refs failed")
                    saveTeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, _teacherState.value?.getOrNull()?.phone, download)
                    onResult(download, null)
                } catch (e: Exception) {
                    Log.e(TAG, "putStream failed", e)
                    onResult(null, e.message ?: e.toString())
                } finally {
                    try { input.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadPhotoWithResolver unexpected", e)
                onResult(null, e.message ?: e.toString())
            }
        }
    }

    // Subir foto para estudiante y actualizar en 'students' y 'users'
    @Suppress("unused")
    fun uploadStudentPhoto(uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }
            try {
                val path = "avatars/$userId.jpg"
                val refs = candidateRefs(path)
                try {
                    var downloadUrl: String? = null
                    var succeeded = false
                    for (r in refs) {
                        try {
                            val snap = r.putFile(uri).await()
                            downloadUrl = snap.storage.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "putFile to candidate ref failed for student, trying next", e)
                        }
                    }
                    if (!succeeded || downloadUrl == null) throw Exception("All candidate refs failed")
                    // actualizar documento students y users
                    try {
                        db.collection("students").document(userId).set(mapOf("avatarUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (e: Exception) { Log.e(TAG, "update students avatar failed", e) }
                    try {
                        db.collection("users").document(userId).set(mapOf("avatarUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (e: Exception) { Log.e(TAG, "update users avatar failed", e) }
                    onResult(downloadUrl, null)
                } catch (e: Exception) {
                    Log.e(TAG, "uploadStudentPhoto failed, trying fallback", e)
                    try {
                        val configuredBucket = FirebaseApp.getInstance().options.storageBucket
                        if (!configuredBucket.isNullOrBlank()) {
                            var altBucket = configuredBucket
                            if (altBucket.contains("firebasestorage.app")) altBucket = altBucket.replace("firebasestorage.app", "appspot.com")
                            val altUrl = if (altBucket.startsWith("gs://")) altBucket else "gs://$altBucket"
                            val altRef = FirebaseStorage.getInstance().getReferenceFromUrl(altUrl).child("avatars/$userId.jpg")
                            val snapshot2 = altRef.putFile(uri).await()
                            val download2 = snapshot2.storage.downloadUrl.await().toString()
                            try { db.collection("students").document(userId).set(mapOf("avatarUrl" to download2), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e3: Exception) { Log.e(TAG, "update students avatar failed", e3) }
                            try { db.collection("users").document(userId).set(mapOf("avatarUrl" to download2), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e3: Exception) { Log.e(TAG, "update users avatar failed", e3) }
                            onResult(download2, null)
                            return@launch
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "uploadStudentPhoto fallback failed", e2)
                        onResult(null, e2.message ?: e2.toString())
                        return@launch
                    }
                    onResult(null, e.message ?: e.toString())
                }
            } catch (_: Exception) {
                onResult(null, "Error desconocido al iniciar la subida")
            }
        }
    }

    // Variante para estudiantes que acepta ContentResolver y usa putFile, con fallback a putStream
    @Suppress("unused")
    fun uploadStudentPhotoWithResolver(contentResolver: ContentResolver, uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }
            val path = "avatars/$userId.jpg"
            val refs = candidateRefs(path)
            try {
                try {
                    var succeeded = false
                    var download: String? = null
                    for (r in refs) {
                        try {
                            r.putFile(uri).await()
                            download = r.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "putFile (with resolver) to candidate ref failed, trying next", e)
                        }
                    }
                    if (!succeeded || download == null) throw Exception("All candidate refs failed")
                    try { db.collection("students").document(userId).set(mapOf("avatarUrl" to download), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.e(TAG, "update students avatar failed", e) }
                    try { db.collection("users").document(userId).set(mapOf("avatarUrl" to download), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.e(TAG, "update users avatar failed", e) }
                    onResult(download, null)
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "putFile failed, trying putStream", e)
                }

                val input = try { contentResolver.openInputStream(uri) } catch (e: Exception) { null }
                if (input == null) { onResult(null, "No se pudo abrir el archivo para subir"); return@launch }
                try {
                    var succeeded = false
                    var download: String? = null
                    for (r in refs) {
                        try {
                            r.putStream(input).await()
                            download = r.downloadUrl.await().toString()
                            succeeded = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "putStream to candidate ref failed, trying next", e)
                        }
                    }
                    if (!succeeded || download == null) throw Exception("All candidate refs failed")
                    try { db.collection("students").document(userId).set(mapOf("avatarUrl" to download), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.e(TAG, "update students avatar failed", e) }
                    try { db.collection("users").document(userId).set(mapOf("avatarUrl" to download), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.e(TAG, "update users avatar failed", e) }
                    onResult(download, null)
                } catch (e: Exception) { Log.e(TAG, "putStream failed for student", e); onResult(null, e.message ?: e.toString()) }
                finally { try { input.close() } catch (_: Exception) {} }
            } catch (e: Exception) {
                Log.e(TAG, "uploadStudentPhotoWithResolver unexpected", e)
                onResult(null, e.message ?: e.toString())
            }
        }
    }

    // Variante que guarda la imagen como Base64 en Firestore (sin usar Firebase Storage).
    // Comprime y escala la imagen para intentar que quepa en el límite de 1MB de Firestore.
    fun uploadPhotoAsBase64WithResolver(contentResolver: ContentResolver, uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }

            // Verificar tamaño del archivo antes de procesar (evitar trabajo pesado innecesario)
            val maxInputBytes = 5_000_000L // 5 MB límite de entrada aceptable
            try {
                val size = getFileSize(contentResolver, uri)
                if (size != null && size > maxInputBytes) {
                    onResult(null, "Archivo demasiado grande: ${size/1_048_576} MB (máx ${maxInputBytes/1_048_576} MB)")
                    return@launch
                }
            } catch (_: Exception) {
                // Si falla la consulta de tamaño, proseguimos con la conversión (es opcional)
            }

            try {
                val base64 = try {
                    // Realizar las operaciones de decodificación/compress en IO
                    withContext(Dispatchers.IO) {
                        // Abrir stream y obtener opciones para muestreo
                        val input1 = contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir el archivo")
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(input1, null, options)
                        try { input1.close() } catch (_: Exception) {}

                        // calcular sampleSize para limitar dimensión máxima (por ejemplo 1024px)
                        val maxDim = 1024
                        var sample = 1
                        val (width, height) = options.outWidth to options.outHeight
                        if (width > 0 && height > 0) {
                            while (width / sample > maxDim || height / sample > maxDim) sample *= 2
                        }

                        val input2 = contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir el archivo (2)")
                        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = BitmapFactory.decodeStream(input2, null, decodeOptions) ?: throw Exception("No se pudo decodificar la imagen")
                        try { input2.close() } catch (_: Exception) {}

                        // Ahora comprimimos iterativamente: bajando calidad y escalando si es necesario
                        // Usamos un límite de bytes crudos más conservador para que la
                        // cadena Base64 resultante (que crece ~4/3) no supere el límite
                        // de 1MiB de Firestore. Elegimos ~700KB para el buffer crudo.
                        val baos = ByteArrayOutputStream()
                        var quality = 90
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        var bytes = baos.toByteArray()

                        // límite de bytes crudos antes de codificar a Base64 (aprox. 700KB)
                        val rawLimit = 700_000
                        while (bytes.size > rawLimit && quality >= 40) {
                            baos.reset()
                            quality -= 10
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                            bytes = baos.toByteArray()
                        }

                        // Si sigue siendo mayor, escalar el bitmap progresivamente (90% cada iteración)
                        var currentBitmap = bitmap
                        while (bytes.size > rawLimit) {
                            val newWidth = (currentBitmap.width * 0.9).toInt().coerceAtLeast(1)
                            val newHeight = (currentBitmap.height * 0.9).toInt().coerceAtLeast(1)
                            val scaled = Bitmap.createScaledBitmap(currentBitmap, newWidth, newHeight, true)
                            if (scaled != currentBitmap) {
                                if (currentBitmap != bitmap) currentBitmap.recycle()
                                currentBitmap = scaled
                            } else break

                            baos.reset()
                            // seguir con la calidad actual (mínimo 30)
                            val q = quality.coerceAtLeast(30)
                            currentBitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
                            bytes = baos.toByteArray()

                            // parada de seguridad: si la imagen es muy pequeña o no mejora, salir
                            if (currentBitmap.width < 50 || currentBitmap.height < 50) break
                        }

                        // Liberar bitmaps si fue escalado
                        if (bitmap != currentBitmap) bitmap.recycle()

                        // Finalmente codificar a Base64 (sin saltos de línea)
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al convertir imagen a Base64", e)
                    throw e
                }

                // Guardar en Firestore: para docentes actualizamos 'teachers' y 'users'
                val dataUrl = "data:image/jpeg;base64,$base64"
                val map = hashMapOf<String, Any?>("photoBase64" to base64, "photoUrl" to dataUrl)
                try {
                    db.collection("teachers").document(userId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) { Log.w(TAG, "No se pudo guardar en 'teachers'", e) }
                try {
                    db.collection("users").document(userId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) { Log.w(TAG, "No se pudo guardar en 'users'", e) }

                // Actualizar estado local para que la UI muestre la dataUrl
                _teacherState.value = Result.success(TeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, auth.currentUser?.email, _teacherState.value?.getOrNull()?.phone, dataUrl))
                onResult(dataUrl, null)
            } catch (e: Exception) {
                Log.e(TAG, "uploadPhotoAsBase64WithResolver failed", e)
                onResult(null, e.message ?: e.toString())
            }
        }
    }

    // Versión para estudiantes: guarda avatarBase64 y avatarUrl en 'students' y 'users'
    fun uploadStudentPhotoAsBase64WithResolver(contentResolver: ContentResolver, uri: Uri, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null, "Usuario no autenticado"); return@launch }

            // Verificar tamaño del archivo antes de procesar
            val maxInputBytes = 5_000_000L // 5 MB
            try {
                val size = getFileSize(contentResolver, uri)
                if (size != null && size > maxInputBytes) {
                    onResult(null, "Archivo demasiado grande: ${size/1_048_576} MB (máx ${maxInputBytes/1_048_576} MB)")
                    return@launch
                }
            } catch (_: Exception) { }

            try {
                val base64 = withContext(Dispatchers.IO) {
                    val input1 = contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir el archivo")
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input1, null, options)
                    try { input1.close() } catch (_: Exception) {}

                    val maxDim = 1024
                    var sample = 1
                    val (width, height) = options.outWidth to options.outHeight
                    if (width > 0 && height > 0) {
                        while (width / sample > maxDim || height / sample > maxDim) sample *= 2
                    }

                    val input2 = contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir el archivo (2)")
                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = BitmapFactory.decodeStream(input2, null, decodeOptions) ?: throw Exception("No se pudo decodificar la imagen")
                    try { input2.close() } catch (_: Exception) {}

                    // Ahora comprimimos iterativamente: bajando calidad y escalando si es necesario
                    // Usamos un límite de bytes crudos más conservador para que la
                    // cadena Base64 resultante (que crece ~4/3) no supere el límite
                    // de 1MiB de Firestore. Elegimos ~700KB para el buffer crudo.
                    val baos = ByteArrayOutputStream()
                    var quality = 90
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    var bytes = baos.toByteArray()

                    // límite de bytes crudos antes de codificar a Base64 (aprox. 700KB)
                    val rawLimit = 700_000
                    while (bytes.size > rawLimit && quality >= 40) {
                        baos.reset()
                        quality -= 10
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        bytes = baos.toByteArray()
                    }

                    // Si sigue siendo mayor, escalar el bitmap progresivamente (90% cada iteración)
                    var currentBitmap = bitmap
                    while (bytes.size > rawLimit) {
                        val newWidth = (currentBitmap.width * 0.9).toInt().coerceAtLeast(1)
                        val newHeight = (currentBitmap.height * 0.9).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(currentBitmap, newWidth, newHeight, true)
                        if (scaled != currentBitmap) {
                            if (currentBitmap != bitmap) currentBitmap.recycle()
                            currentBitmap = scaled
                        } else break

                        baos.reset()
                        // seguir con la calidad actual (mínimo 30)
                        val q = quality.coerceAtLeast(30)
                        currentBitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
                        bytes = baos.toByteArray()

                        // parada de seguridad: si la imagen es muy pequeña o no mejora, salir
                        if (currentBitmap.width < 50 || currentBitmap.height < 50) break
                    }

                    // Liberar bitmaps si fue escalado
                    if (bitmap != currentBitmap) bitmap.recycle()

                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }

                val dataUrl = "data:image/jpeg;base64,$base64"
                try { db.collection("students").document(userId).set(mapOf("avatarBase64" to base64, "avatarUrl" to dataUrl), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.w(TAG, "No se pudo guardar en 'students'", e) }
                try { db.collection("users").document(userId).set(mapOf("avatarBase64" to base64, "avatarUrl" to dataUrl), com.google.firebase.firestore.SetOptions.merge()).await() } catch (e: Exception) { Log.w(TAG, "No se pudo guardar en 'users'", e) }

                onResult(dataUrl, null)
            } catch (e: Exception) {
                Log.e(TAG, "uploadStudentPhotoAsBase64WithResolver failed", e)
                onResult(null, e.message ?: e.toString())
            }
        }
    }

    // Helper público para obtener el email current del usuario (UI lo consulta)
    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    // Helper para obtener el tamaño del archivo desde un Uri (si disponible via OpenableColumns)
    private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long? {
        var size: Long? = null
        val cursor = try { contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null) } catch (e: Exception) { null }
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.SIZE)
                if (idx != -1) {
                    val s = it.getLong(idx)
                    if (s >= 0) size = s
                }
            }
        }
        return size
    }

    private fun loadRoleFromDb() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                // 1) Intentar en users/{uid}
                val udoc = db.collection("users").document(uid).get().await()
                var role = udoc.getString("role")
                if (role.isNullOrBlank()) {
                    // 2) Fallback por colecciones conocidas
                    val collections = listOf("students", "teachers", "parents", "admins")
                    for (coll in collections) {
                        val d = db.collection(coll).document(uid).get().await()
                        if (d.exists()) {
                            role = d.getString("role") ?: when (coll) {
                                "students" -> "ESTUDIANTE"
                                "teachers" -> "DOCENTE"
                                "parents" -> "PADRE"
                                "admins" -> "ADMIN"
                                else -> null
                            }
                            if (!role.isNullOrBlank()) break
                        }
                    }
                }
                _roleString.value = role?.uppercase()
            } catch (_: Exception) {
                // dejar null si falla
            }
        }
    }

    // Public: forzar recarga completa de datos de perfil (útil si el ViewModel existía antes de autenticación)
    fun refreshAllData() {
        viewModelScope.launch {
            Log.d(TAG, "refreshAllData: iniciando recarga de student/teacher/children/role")
            // Llamamos a los métodos privados que inician sus propios coroutines para actualizar los StateFlows
            loadStudentData()
            loadTeacherData()
            loadChildrenForParent()
            loadRoleFromDb()
        }
    }

    // Hacer pública la recarga de datos de estudiante para que la UI pueda forzarla cuando sea necesario
    fun reloadStudentData() {
        loadStudentData()
    }
}
