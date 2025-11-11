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
import com.google.firebase.firestore.SetOptions

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

    // Índice del hijo seleccionado por el padre (nullable hasta que se carguen hijos)
    private val _selectedChildIndex = MutableStateFlow<Int?>(null)
    val selectedChildIndex: StateFlow<Int?> = _selectedChildIndex

    fun selectChildAtIndex(index: Int) {
        val list = _children.value
        if (index < 0 || index >= list.size) return
        _selectedChildIndex.value = index
        _student.value = Result.success(list[index])
    }

    // Rol del usuario cargado desde Firestore (users y colecciones específicas)
    private val _roleString = MutableStateFlow<String?>(null)
    val roleString: StateFlow<String?> = _roleString

    // Auth listener to react when user signs in after VM creation
    private val authStateListener = FirebaseAuth.AuthStateListener {
        // When auth state changes, re-fetch relevant data (only if user is signed in)
        if (it.currentUser != null) {
            loadStudentData()
            loadTeacherData()
            loadChildrenForParent()
            loadRoleFromDb()
        } else {
            // clear state on sign out
            _student.value = Result.success(null)
            _children.value = emptyList()
            _selectedChildIndex.value = null
            _roleString.value = null
        }
    }

    init {
        // Subscribe to auth state to reload data when user signs in/out
        auth.addAuthStateListener(authStateListener)

        loadStudentData()
        loadTeacherData()
        loadChildrenForParent()
        loadRoleFromDb()
    }

    override fun onCleared() {
        super.onCleared()
        try { auth.removeAuthStateListener(authStateListener) } catch (_: Exception) { }
    }

    private fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val studentDocSnap = db.collection("students").document(userId).get().await()
                    if (studentDocSnap.exists()) {
                        Log.d(TAG, "loadStudentData: found students/$userId")
                        // Mapear manualmente para soportar campos 'nombre' o 'name'
                        val studentData = Student(
                            id = userId,
                            nombre = com.example.appcolegios.util.FirestoreUtils.getPreferredName(studentDocSnap) ?: "",
                            curso = studentDocSnap.getString("curso") ?: studentDocSnap.getString("course") ?: "",
                            grupo = studentDocSnap.getString("grupo") ?: studentDocSnap.getString("group") ?: "",
                            promedio = try { (studentDocSnap.getDouble("promedio") ?: studentDocSnap.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 },
                            avatarUrl = studentDocSnap.getString("avatarUrl") ?: studentDocSnap.getString("photoUrl") ?: studentDocSnap.getString("avatar")
                        )

                        // Intentar leer users/{uid} para completar/actualizar curso/grupo si existen allí
                        try {
                            val userDoc = db.collection("users").document(userId).get().await()
                            if (userDoc.exists()) {
                                val cursoFromUser = userDoc.getString("curso") ?: userDoc.getString("course")
                                val grupoFromUser = userDoc.getString("grupo") ?: userDoc.getString("group")
                                val merged = studentData.copy(
                                    curso = cursoFromUser ?: studentData.curso,
                                    grupo = grupoFromUser ?: studentData.grupo,
                                    nombre = studentData.nombre.ifBlank { com.example.appcolegios.util.FirestoreUtils.getPreferredName(userDoc) ?: studentData.nombre },
                                    avatarUrl = studentData.avatarUrl ?: (userDoc.getString("avatarUrl") ?: userDoc.getString("photoUrl") ?: userDoc.getString("avatar"))
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
                                val rawAvatarUrl = userDoc.getString("avatarUrl") ?: userDoc.getString("photoUrl") ?: userDoc.getString("avatar")
                                val avatarBase64 = userDoc.getString("avatarBase64")
                                val avatar = when {
                                    !rawAvatarUrl.isNullOrBlank() -> rawAvatarUrl
                                    !avatarBase64.isNullOrBlank() -> {
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
                    val nombre = com.example.appcolegios.util.FirestoreUtils.getPreferredName(doc)
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

                // 1) Buscar en students colección por array 'parents' que contenga userId
                if (!userId.isNullOrBlank()) {
                    try {
                        val qParents = db.collection("students").whereArrayContains("parents", userId).get().await()
                        for (doc in qParents.documents) {
                            // Mapear manualmente para aceptar 'nombre' o 'name'
                            val id = doc.id
                            val name = doc.getString("nombre") ?: doc.getString("name") ?: doc.getString("displayName") ?: ""
                            val curso = doc.getString("curso") ?: doc.getString("course") ?: ""
                            val grupo = doc.getString("grupo") ?: doc.getString("group") ?: ""
                            val rawAvatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("avatar")
                            val avatarBase64 = doc.getString("avatarBase64")
                            val avatar = when {
                                !rawAvatar.isNullOrBlank() -> rawAvatar
                                !avatarBase64.isNullOrBlank() -> if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                else -> null
                            }
                            val promedio = try { (doc.getDouble("promedio") ?: doc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                            val mapped = Student(
                                id = id,
                                nombre = name,
                                curso = curso,
                                grupo = grupo,
                                promedio = promedio,
                                avatarUrl = avatar
                            )
                            childrenList.add(mapped)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadChildrenForParent: error querying students.parents", e)
                    }
                }

                // 2) Buscar en students por 'acudienteId' (compatibilidad con esquemas anteriores)
                if (!userId.isNullOrBlank()) {
                    try {
                        val byIdQuery = db.collection("students").whereEqualTo("acudienteId", userId).get().await()
                        for (doc in byIdQuery.documents) {
                            val id = doc.id
                            val name = doc.getString("nombre") ?: doc.getString("name") ?: doc.getString("displayName") ?: ""
                            val curso = doc.getString("curso") ?: doc.getString("course") ?: ""
                            val grupo = doc.getString("grupo") ?: doc.getString("group") ?: ""
                            val rawAvatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("avatar")
                            val avatarBase64 = doc.getString("avatarBase64")
                            val avatar = when {
                                !rawAvatar.isNullOrBlank() -> rawAvatar
                                !avatarBase64.isNullOrBlank() -> if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                else -> null
                            }
                            val promedio = try { (doc.getDouble("promedio") ?: doc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                            val mapped = Student(
                                id = id,
                                nombre = name,
                                curso = curso,
                                grupo = grupo,
                                promedio = promedio,
                                avatarUrl = avatar
                            )
                            childrenList.add(mapped)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadChildrenForParent: error querying students.acudienteId", e)
                    }
                }

                // 3) Buscar en students por 'acudienteEmail' como fallback
                if (!userEmail.isNullOrBlank()) {
                    try {
                        val byEmailQuery = db.collection("students").whereEqualTo("acudienteEmail", userEmail).get().await()
                        for (doc in byEmailQuery.documents) {
                            val id = doc.id
                            val name = doc.getString("nombre") ?: doc.getString("name") ?: doc.getString("displayName") ?: ""
                            val curso = doc.getString("curso") ?: doc.getString("course") ?: ""
                            val grupo = doc.getString("grupo") ?: doc.getString("group") ?: ""
                            val rawAvatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("avatar")
                            val avatarBase64 = doc.getString("avatarBase64")
                            val avatar = when {
                                !rawAvatar.isNullOrBlank() -> rawAvatar
                                !avatarBase64.isNullOrBlank() -> if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                else -> null
                            }
                            val promedio = try { (doc.getDouble("promedio") ?: doc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                            val mapped = Student(
                                id = id,
                                nombre = name,
                                curso = curso,
                                grupo = grupo,
                                promedio = promedio,
                                avatarUrl = avatar
                            )
                            childrenList.add(mapped)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadChildrenForParent: error querying students.acudienteEmail", e)
                    }
                }

                // 4) Buscar en users: parents array contiene userId --> mapear a Student
                if (!userId.isNullOrBlank()) {
                    try {
                        val usersParents = db.collection("users").whereArrayContains("parents", userId).get().await()
                        for (doc in usersParents.documents) {
                            try {
                                val name = doc.getString("name") ?: doc.getString("displayName") ?: ""
                                val curso = doc.getString("curso") ?: doc.getString("course") ?: ""
                                val grupo = doc.getString("grupo") ?: doc.getString("group") ?: ""
                                val rawAvatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("avatar")
                                val avatarBase64 = doc.getString("avatarBase64")
                                val avatar = when {
                                    !rawAvatar.isNullOrBlank() -> rawAvatar
                                    !avatarBase64.isNullOrBlank() -> if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                    else -> null
                                }
                                val promedio = try { (doc.getDouble("promedio") ?: doc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                                val mapped = Student(
                                    id = doc.id,
                                    nombre = name,
                                    curso = curso,
                                    grupo = grupo,
                                    promedio = promedio,
                                    avatarUrl = avatar
                                )
                                childrenList.add(mapped)
                            } catch (e: Exception) {
                                Log.w(TAG, "loadChildrenForParent: error mapping user doc to Student (parents)", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadChildrenForParent: error querying users.parents", e)
                    }
                }

                // 5) Buscar en users por 'acudienteEmail' para mapear hijos que solo existen en users
                if (!userEmail.isNullOrBlank()) {
                    try {
                        val usersByAcudienteEmail = db.collection("users").whereEqualTo("acudienteEmail", userEmail).get().await()
                        for (doc in usersByAcudienteEmail.documents) {
                            try {
                                val name = doc.getString("name") ?: doc.getString("displayName") ?: ""
                                val curso = doc.getString("curso") ?: doc.getString("course") ?: ""
                                val grupo = doc.getString("grupo") ?: doc.getString("group") ?: ""
                                val rawAvatar = doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("avatar")
                                val avatarBase64 = doc.getString("avatarBase64")
                                val avatar = when {
                                    !rawAvatar.isNullOrBlank() -> rawAvatar
                                    !avatarBase64.isNullOrBlank() -> if (avatarBase64.startsWith("data:")) avatarBase64 else "data:image/jpeg;base64,$avatarBase64"
                                    else -> null
                                }
                                val promedio = try { (doc.getDouble("promedio") ?: doc.getLong("promedio")?.toDouble() ?: 0.0) } catch (_: Exception) { 0.0 }
                                val mapped = Student(
                                    id = doc.id,
                                    nombre = name,
                                    curso = curso,
                                    grupo = grupo,
                                    promedio = promedio,
                                    avatarUrl = avatar
                                )
                                childrenList.add(mapped)
                            } catch (e: Exception) {
                                Log.w(TAG, "loadChildrenForParent: error mapping user doc to Student (acudienteEmail)", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "loadChildrenForParent: error querying users.acudienteEmail", e)
                    }
                }

                // Eliminar duplicados por id y actualizar estado
                val deduped = childrenList.distinctBy { it.id }

                // Rellenar nombres faltantes consultando users/{id} o students/{id}
                val finalList = mutableListOf<Student>()
                for (s in deduped) {
                    if (s.nombre.isBlank()) {
                        try {
                            // Preferir students/{id} si existe
                            val stDoc = try { db.collection("students").document(s.id).get().await() } catch (_: Exception) { null }
                            val nameFromStudents = stDoc?.getString("nombre") ?: stDoc?.getString("name") ?: stDoc?.getString("displayName")
                            if (!nameFromStudents.isNullOrBlank()) {
                                finalList.add(s.copy(nombre = nameFromStudents))
                                continue
                            }
                            val uDoc = try { db.collection("users").document(s.id).get().await() } catch (_: Exception) { null }
                            val nameFromUsers = uDoc?.getString("name") ?: uDoc?.getString("displayName")
                            if (!nameFromUsers.isNullOrBlank()) {
                                finalList.add(s.copy(nombre = nameFromUsers))
                                continue
                            }
                            finalList.add(s)
                        } catch (_: Exception) {
                            finalList.add(s)
                        }
                    } else {
                        finalList.add(s)
                    }
                }

                val resolved = finalList.toList()
                _children.value = resolved

                if (resolved.isNotEmpty()) {
                    // Inicializar selección si aún no existe
                    if (_selectedChildIndex.value == null) {
                        _selectedChildIndex.value = 0
                    }
                    val idx = _selectedChildIndex.value ?: 0
                    _student.value = Result.success(resolved.getOrNull(idx) ?: resolved[0])
                } else {
                    _student.value = Result.success(null)
                }

            } catch (e: Exception) {
                Log.w(TAG, "loadChildrenForParent: fallo inesperado", e)
                _children.value = emptyList()
            }
        }
    }

    // --- Funciones añadidas para compatibilidad con ProfileScreen.kt ---

    private fun loadRoleFromDb() {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    _roleString.value = null
                    return@launch
                }
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val role = doc.getString("role") ?: doc.getString("rol") ?: doc.getString("roleString")
                    _roleString.value = role
                } else {
                    _roleString.value = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadRoleFromDb: error", e)
                _roleString.value = null
            }
        }
    }

    fun refreshAllData() {
        // Reusar las funciones ya existentes que lanzan coroutines internas
        loadStudentData()
        loadTeacherData()
        loadChildrenForParent()
        loadRoleFromDb()
    }

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun saveTeacherProfile(name: String?, phone: String?, photoUrl: String?) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) return@launch
            try {
                val teacherRef = db.collection("teachers").document(uid)
                val userRef = db.collection("users").document(uid)
                val updates = mutableMapOf<String, Any?>()
                if (name != null) updates["name"] = name
                if (phone != null) updates["phone"] = phone

                if (!photoUrl.isNullOrBlank()) {
                    if (photoUrl.startsWith("data:")) {
                        // Almacenar como photoBase64 (sin el prefijo 'data:...;base64,')
                        val base64 = photoUrl.substringAfter(",", photoUrl)
                        updates["photoBase64"] = base64
                        // también guardar un campo legible
                        updates["photoUrl"] = null
                    } else {
                        updates["photoUrl"] = photoUrl
                        updates["photoBase64"] = null
                    }
                }

                if (updates.isNotEmpty()) {
                    teacherRef.set(updates, SetOptions.merge()).await()
                    userRef.set(updates.mapKeys { if (it.key == "photoBase64") "photoBase64" else it.key }, SetOptions.merge()).await()
                }

                // Refrescar estado local
                loadTeacherData()
            } catch (e: Exception) {
                Log.w(TAG, "saveTeacherProfile: error", e)
            }
        }
    }

    fun uploadPhotoAsBase64WithResolver(resolver: ContentResolver, uri: Uri, callback: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val bytesAndMime = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: throw Exception("No se puede abrir el archivo")
                    val raw = input.use { it.readBytes() }
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    // Si es muy grande, intentar recomprimir
                    val finalBytes = if (raw.size > 500_000) {
                        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        baos.toByteArray()
                    } else raw
                    Pair(finalBytes, mime)
                }

                val (finalBytes, mime) = bytesAndMime
                val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
                val dataUri = "data:$mime;base64,$base64"

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    callback(null, "Usuario no identificado")
                    return@launch
                }

                val teacherRef = db.collection("teachers").document(uid)
                val userRef = db.collection("users").document(uid)
                val map = mapOf(
                    "photoBase64" to base64
                )
                teacherRef.set(map, SetOptions.merge()).await()
                userRef.set(map, SetOptions.merge()).await()

                // Actualizar estado local
                loadTeacherData()

                callback(dataUri, null)
            } catch (e: Exception) {
                Log.w(TAG, "uploadPhotoAsBase64WithResolver: error", e)
                callback(null, e.message ?: "Error desconocido")
            }
        }
    }

    fun uploadStudentPhotoAsBase64WithResolver(resolver: ContentResolver, uri: Uri, callback: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val bytesAndMime = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: throw Exception("No se puede abrir el archivo")
                    val raw = input.use { it.readBytes() }
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val finalBytes = if (raw.size > 500_000) {
                        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        baos.toByteArray()
                    } else raw
                    Pair(finalBytes, mime)
                }

                val (finalBytes, mime) = bytesAndMime
                val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
                val dataUri = "data:$mime;base64,$base64"

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    callback(null, "Usuario no identificado")
                    return@launch
                }

                val userRef = db.collection("users").document(uid)
                val studentsRef = db.collection("students").document(uid)

                val map = mapOf(
                    "avatarBase64" to base64,
                    "avatar" to dataUri
                )

                userRef.set(map, SetOptions.merge()).await()
                studentsRef.set(map, SetOptions.merge()).await()

                // Refrescar student y demás
                loadStudentData()

                callback(dataUri, null)
            } catch (e: Exception) {
                Log.w(TAG, "uploadStudentPhotoAsBase64WithResolver: error", e)
                callback(null, e.message ?: "Error desconocido")
            }
        }
    }

}
