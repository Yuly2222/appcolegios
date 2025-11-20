package com.example.appcolegios.perfil

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Student
import com.example.appcolegios.data.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.content.ContentResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.storage.StorageReference
import com.google.firebase.FirebaseApp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val repo = FirestoreRepository()
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
    @Suppress("unused")
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
            // limpiar estado relacionado con nombres en memoria si aplica

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

    private fun normalizeCourseKey(raw: String?): Pair<String, String> {
        val r = raw?.trim() ?: ""
        if (r.contains("-")) {
            val parts = r.split("-")
            val cursoPart = parts.getOrNull(0)?.trim() ?: ""
            val grupoPart = parts.getOrNull(1)?.trim() ?: ""
            val grupoUpper = if (grupoPart.isNotBlank()) grupoPart.uppercase() else ""
            val cursoDisplay = if (cursoPart.isNotBlank()) {
                if (grupoUpper.isNotBlank()) "${cursoPart}-${grupoUpper}" else cursoPart
            } else r
            return Pair(cursoDisplay, grupoUpper)
        }
        // si no contiene '-', intentar usar raw curso y dejar grupo vacío
        return Pair(r, "")
    }

    private fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val studentMap = repo.getDocumentData("students", userId)
                    if (studentMap != null) {
                        Log.d(TAG, "loadStudentData: found students/$userId")
                        // Mapear manualmente para soportar campos 'nombre' o 'name'
                        val rawCurso = studentMap["curso"] as? String ?: (studentMap["course"] as? String ?: "")
                        val rawGrupo = studentMap["grupo"] as? String ?: (studentMap["group"] as? String ?: "")
                        val (cursoFromRaw, grupoFromRaw) = normalizeCourseKey(rawCurso)

                        val preferredName = (studentMap["nombre"] as? String) ?: (studentMap["name"] as? String) ?: (studentMap["displayName"] as? String)
                        val promedioVal = try { (studentMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
                        val avatarVal = studentMap["avatarUrl"] as? String ?: studentMap["photoUrl"] as? String ?: studentMap["avatar"] as? String
                        val studentData = Student(
                            id = userId,
                            nombre = preferredName ?: "",
                            curso = if (cursoFromRaw.isNotBlank()) cursoFromRaw else (studentMap["curso"] as? String ?: studentMap["course"] as? String ?: ""),
                            grupo = if (grupoFromRaw.isNotBlank()) grupoFromRaw else rawGrupo,
                            promedio = promedioVal,
                            avatarUrl = avatarVal
                        )

                        // Si no hay curso/grupo explícitos, intentar desde el array 'grupos' (compatibilidad con AssignGroupAdminScreen)
                        val gruposArray = try { studentMap["grupos"] as? List<*> } catch (_: Exception) { null }
                        val fallbackFromGrupos = if ((studentData.curso.isBlank() || studentData.grupo.isBlank()) && !gruposArray.isNullOrEmpty()) {
                            val first = gruposArray.firstOrNull()?.toString()?.trim() ?: ""
                            if (first.contains("-")) {
                                val parts = first.split("-")
                                val cursoPart = parts.getOrNull(0)?.trim() ?: ""
                                val grupoPart = parts.getOrNull(1)?.trim() ?: ""
                                val cursoDisplay = if (cursoPart.isNotBlank() && grupoPart.isNotBlank()) "${cursoPart}-${grupoPart.uppercase()}" else first
                                studentData.copy(curso = cursoDisplay, grupo = grupoPart.uppercase())
                            } else {
                                studentData.copy(curso = first)
                            }
                        } else studentData

                        // Intentar leer users/{uid} para completar/actualizar curso/grupo si existen allí
                        try {
                            val userDocMap = repo.getDocumentData("users", userId)
                            if (userDocMap != null) {
                                val cursoFromUserRaw = userDocMap["curso"] as? String ?: userDocMap["course"] as? String
                                val grupoFromUserRaw = userDocMap["grupo"] as? String ?: userDocMap["group"] as? String
                                val (cursoNormalized, grupoNormalized) = normalizeCourseKey(cursoFromUserRaw ?: "")
                                var merged = fallbackFromGrupos.copy(
                                     curso = if (cursoNormalized.isNotBlank()) cursoNormalized else fallbackFromGrupos.curso,
                                     grupo = if (grupoNormalized.isNotBlank()) grupoNormalized else (grupoFromUserRaw ?: fallbackFromGrupos.grupo),
                                     nombre = fallbackFromGrupos.nombre.ifBlank { (userDocMap["fullName"] as? String) ?: fallbackFromGrupos.nombre },
                                     avatarUrl = fallbackFromGrupos.avatarUrl ?: (userDocMap["avatarUrl"] as? String ?: userDocMap["photoUrl"] as? String ?: userDocMap["avatar"] as? String)
                                 )
                                 // Si aún no hay curso/grupo, intentar obtenerlos desde users.grupos
                                 if ((merged.curso.isBlank() || merged.grupo.isBlank())) {
                                     val userGrupos = try { userDocMap["grupos"] as? List<*> } catch (_: Exception) { null }
                                     if (!userGrupos.isNullOrEmpty()) {
                                         val first = userGrupos.firstOrNull()?.toString()?.trim() ?: ""
                                         if (first.isNotBlank()) {
                                             if (first.contains("-")) {
                                                 val parts = first.split("-")
                                                 val cursoPart = parts.getOrNull(0)?.trim() ?: ""
                                                 val grupoPart = parts.getOrNull(1)?.trim() ?: ""
                                                 val cursoDisplay = if (cursoPart.isNotBlank() && grupoPart.isNotBlank()) "${cursoPart}-${grupoPart.uppercase()}" else first
                                                merged = merged.copy(curso = cursoDisplay, grupo = grupoPart.uppercase())
                                              merged = merged.copy(curso = cursoDisplay, grupo = grupoPart.uppercase())
                                             } else {
                                               merged = merged.copy(curso = first)
                                                merged = merged.copy(curso = first)
                                             }
                                         }
                                     }
                                 }

                                 Log.d(TAG, "loadStudentData: merged Student from students/$userId + users/$userId -> $merged")
                                 _student.value = Result.success(merged)
                                 return@launch
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "loadStudentData: error reading users/$userId while merging", e)
                        }
                         _student.value = Result.success(fallbackFromGrupos)
                     } else {
                        // students/$userId no existe: no poblar _student con datos de users/{userId}
                        // Esto evita que el ViewModel muestre temporalmente el nombre del padre en pantallas de PADRE.
                        Log.d(TAG, "loadStudentData: students/$userId not found, skipping users fallback to avoid showing parent as student")
                        _student.value = Result.success(null)
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
                val teacherMap = repo.getDocumentData("teachers", userId)
                if (teacherMap != null) {
                    val nombre = teacherMap["fullName"] as? String ?: teacherMap["name"] as? String
                    val email = teacherMap["email"] as? String ?: auth.currentUser?.email
                    val phone = teacherMap["phone"] as? String
                    val photoBase64 = teacherMap["photoBase64"] as? String
                    val photo = if (!photoBase64.isNullOrBlank()) {
                        "data:image/jpeg;base64,$photoBase64"
                    } else {
                        teacherMap["photoUrl"] as? String ?: teacherMap["avatar"] as? String
                    }
                    _teacherState.value = Result.success(TeacherProfile(nombre, email, phone, photo))
                    return@launch
                }

                val userDocMap = repo.getDocumentData("users", userId)
                if (userDocMap != null) {
                    val nombre = userDocMap["displayName"] as? String ?: userDocMap["name"] as? String
                    val email = userDocMap["email"] as? String ?: auth.currentUser?.email
                    val phone = userDocMap["phone"] as? String
                    val photoBase64 = userDocMap["photoBase64"] as? String
                    val photo = if (!photoBase64.isNullOrBlank()) {
                        "data:image/jpeg;base64,$photoBase64"
                    } else {
                        userDocMap["photoUrl"] as? String ?: userDocMap["avatar"] as? String
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

    // Cargar hijos asociados al padre actual. Busca en 'students' por 'parents' que contenga userId
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
                        val studentsWithParent = repo.queryWhereArrayContains("students", "parents", userId)
                        for (docMap in studentsWithParent) {
                            val id = docMap["__id"] as? String ?: continue
                            val name = docMap["nombre"] as? String ?: docMap["name"] as? String ?: docMap["displayName"] as? String ?: ""
                            val curso = docMap["curso"] as? String ?: docMap["course"] as? String ?: ""
                            val grupo = docMap["grupo"] as? String ?: docMap["group"] as? String ?: ""
                            val rawAvatar = docMap["avatarUrl"] as? String ?: docMap["photoUrl"] as? String ?: docMap["avatar"] as? String
                            val avatarBase64 = docMap["avatarBase64"] as? String
                            val avatar = rawAvatar?.takeIf { it.isNotBlank() } ?: avatarBase64?.let { ab -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab" }
                            val promedio = try { (docMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
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
                        val byIdQuery = repo.queryWhereEqual("students", "acudienteId", userId)
                        for (docMap in byIdQuery) {
                            val id = docMap["__id"] as? String ?: continue
                            val name = docMap["nombre"] as? String ?: docMap["name"] as? String ?: docMap["displayName"] as? String ?: ""
                            val curso = docMap["curso"] as? String ?: docMap["course"] as? String ?: ""
                            val grupo = docMap["grupo"] as? String ?: docMap["group"] as? String ?: ""
                            val rawAvatar = docMap["avatarUrl"] as? String ?: docMap["photoUrl"] as? String ?: docMap["avatar"] as? String
                            val avatarBase64 = docMap["avatarBase64"] as? String
                            val avatar = rawAvatar?.takeIf { it.isNotBlank() } ?: avatarBase64?.let { ab -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab" }
                            val promedio = try { (docMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
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
                        val byEmailQuery = repo.queryWhereEqual("students", "acudienteEmail", userEmail)
                        for (docMap in byEmailQuery) {
                            val id = docMap["__id"] as? String ?: continue
                            val name = docMap["nombre"] as? String ?: docMap["name"] as? String ?: docMap["displayName"] as? String ?: ""
                            val curso = docMap["curso"] as? String ?: docMap["course"] as? String ?: ""
                            val grupo = docMap["grupo"] as? String ?: docMap["group"] as? String ?: ""
                            val rawAvatar = docMap["avatarUrl"] as? String ?: docMap["photoUrl"] as? String ?: docMap["avatar"] as? String
                            val avatarBase64 = docMap["avatarBase64"] as? String
                            val avatar = rawAvatar?.takeIf { it.isNotBlank() } ?: avatarBase64?.let { ab -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab" }
                            val promedio = try { (docMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
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
                        val usersParents = repo.queryWhereArrayContains("users", "parents", userId)
                        for (docMap in usersParents) {
                            try {
                                val name = docMap["name"] as? String ?: docMap["displayName"] as? String ?: ""
                                val curso = docMap["curso"] as? String ?: docMap["course"] as? String ?: ""
                                val grupo = docMap["grupo"] as? String ?: docMap["group"] as? String ?: ""
                                val rawAvatar = docMap["avatarUrl"] as? String ?: docMap["photoUrl"] as? String ?: docMap["avatar"] as? String
                                val avatarBase64 = docMap["avatarBase64"] as? String
                                val avatar = rawAvatar?.takeIf { it.isNotBlank() } ?: avatarBase64?.let { ab -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab" }
                                val promedio = try { (docMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
                                val mapped = Student(
                                    id = docMap["__id"] as? String ?: "",
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
                        val usersByAcudienteEmail = repo.queryWhereEqual("users", "acudienteEmail", userEmail)
                        for (docMap in usersByAcudienteEmail) {
                            try {
                                val name = docMap["name"] as? String ?: docMap["displayName"] as? String ?: ""
                                val curso = docMap["curso"] as? String ?: docMap["course"] as? String ?: ""
                                val grupo = docMap["grupo"] as? String ?: docMap["group"] as? String ?: ""
                                val rawAvatar = docMap["avatarUrl"] as? String ?: docMap["photoUrl"] as? String ?: docMap["avatar"] as? String
                                val avatarBase64 = docMap["avatarBase64"] as? String
                                val avatar = rawAvatar?.takeIf { it.isNotBlank() } ?: avatarBase64?.let { ab -> if (ab.startsWith("data:")) ab else "data:image/jpeg;base64,$ab" }
                                val promedio = try { (docMap["promedio"] as? Number)?.toDouble() ?: 0.0 } catch (_: Exception) { 0.0 }
                                val mapped = Student(
                                    id = docMap["__id"] as? String ?: "",
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
                            val stDocMap = repo.getDocumentData("students", s.id)
                            val nameFromStudents = stDocMap?.let { it["nombre"] as? String ?: it["name"] as? String ?: it["displayName"] as? String } ?: ""
                            if (nameFromStudents.isNotBlank()) {
                                finalList.add(s.copy(nombre = nameFromStudents))
                                continue
                            }
                            val uDocMap = repo.getDocumentData("users", s.id)
                            val nameFromUsers = uDocMap?.let { it["name"] as? String ?: it["displayName"] as? String } ?: ""
                            if (nameFromUsers.isNotBlank()) {
                                finalList.add(s.copy(nombre = nameFromUsers))
                                continue
                            }
                        } catch (_: Exception) {
                        }
                    }
                    finalList.add(s)
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
                val userMap = repo.getDocumentData("users", uid)
                if (userMap != null) {
                    val role = userMap["role"] as? String ?: userMap["rol"] as? String ?: userMap["roleString"] as? String
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
                val updates = mutableMapOf<String, Any?>()
                if (name != null) updates["name"] = name
                if (phone != null) updates["phone"] = phone

                if (!photoUrl.isNullOrBlank()) {
                    if (photoUrl.startsWith("data:")) {
                        // decode base64 and upload to Storage, then set photoUrl to the storage download URL
                        val base64 = photoUrl.substringAfter(",", photoUrl)
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            val filename = "users/$uid/photo_${System.currentTimeMillis()}.jpg"
                            val ref = storage.reference.child(filename)
                            ref.putBytes(bytes).addOnSuccessListener {
                                ref.downloadUrl.addOnSuccessListener { dl ->
                                    updates["photoUrl"] = dl.toString()
                                    // apply updates to both teachers and users
                                    if (updates.isNotEmpty()) {
                                        repo.setDocumentFields("teachers", uid, updates)
                                        repo.setDocumentFields("users", uid, updates)
                                    }
                                }.addOnFailureListener {
                                    // fallback: don't store base64 in Firestore
                                }
                            }.addOnFailureListener {
                                // upload failed
                            }
                        } catch (e: Exception) {
                            // decoding/upload failed; skip storing base64
                        }
                        // return early because we already triggered async upload above
                        // and we'll refresh teacher data when downloadUrl callback completes
                    } else {
                        // Remote URL: just save it
                        updates["photoUrl"] = photoUrl
                    }
                }

                // If updates contains only async upload placeholder, the repo update will be done in the upload callback.
                if (updates.isNotEmpty()) {
                    // ensure we don't write large base64 blobs
                    val filtered = updates.filterKeys { k -> k != "photoBase64" }
                    if (filtered.isNotEmpty()) {
                        repo.setDocumentFields("teachers", uid, filtered)
                        repo.setDocumentFields("users", uid, filtered)
                    }
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
                    val finalBytes = if (raw.size > 500_000) {
                        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        baos.toByteArray()
                    } else raw
                    Pair(finalBytes, mime)
                }

                val (finalBytes, mime) = bytesAndMime

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    callback(null, "Usuario no identificado")
                    return@launch
                }

                // upload to Firebase Storage instead of writing base64 to Firestore
                val filename = "users/$uid/photo_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child(filename)
                ref.putBytes(finalBytes).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { dl ->
                        val url = dl.toString()
                        val map = mapOf("photoUrl" to url)
                        repo.setDocumentFields("teachers", uid, map)
                        repo.setDocumentFields("users", uid, map)
                        loadTeacherData()
                        callback(url, null)
                    }.addOnFailureListener { e ->
                        callback(null, e.message ?: "Error obteniendo URL")
                    }
                }.addOnFailureListener { e ->
                    callback(null, e.message ?: "Error subiendo imagen")
                }
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

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    callback(null, "Usuario no identificado")
                    return@launch
                }

                val filename = "students/$uid/avatar_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child(filename)
                ref.putBytes(finalBytes).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { dl ->
                        val url = dl.toString()
                        val map = mapOf(
                            "avatarUrl" to url,
                            "avatar" to url
                        )
                        repo.setDocumentFields("users", uid, map)
                        repo.setDocumentFields("students", uid, map)
                        loadStudentData()
                        callback(url, null)
                    }.addOnFailureListener { e ->
                        callback(null, e.message ?: "Error obteniendo URL")
                    }
                }.addOnFailureListener { e ->
                    callback(null, e.message ?: "Error subiendo imagen")
                }
            } catch (e: Exception) {
                Log.w(TAG, "uploadStudentPhotoAsBase64WithResolver: error", e)
                callback(null, e.message ?: "Error desconocido")
            }
        }
    }

    // Utilidad administrativa: si existen campos *Base64 pero no existe el campo legible (photoUrl/avatarUrl), rellenarlos
    fun backfillMissingPhotoUrls(callback: (String) -> Unit = {}) {
        viewModelScope.launch {
            var updated = 0
            try {
                // users
                try {
                    val usersDocs = repo.getAllDocuments("users")
                    for (doc in usersDocs) {
                        try {
                            val id = doc["__id"] as? String ?: continue
                            val pb = (doc["photoBase64"] as? String) ?: (doc["photo_base64"] as? String)
                            val pr = doc["photoUrl"] as? String ?: doc["photourl"] as? String
                            if (!pb.isNullOrBlank() && pr.isNullOrBlank()) {
                                // upload to storage
                                try {
                                    val base64 = if (pb.startsWith("data:")) pb.substringAfter(",") else pb
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    val filename = "users/$id/photo_${System.currentTimeMillis()}.jpg"
                                    val ref = storage.reference.child(filename)
                                    ref.putBytes(bytes).addOnSuccessListener {
                                        ref.downloadUrl.addOnSuccessListener { dl ->
                                            val url = dl.toString()
                                            repo.setDocumentFields("users", id, mapOf("photoUrl" to url))
                                            repo.deleteFields("users", id, listOf("photoBase64", "photo_base64"))
                                        }
                                    }
                                    updated++
                                } catch (_: Exception) { }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                // teachers
                try {
                    val teacherDocs = repo.getAllDocuments("teachers")
                    for (doc in teacherDocs) {
                        try {
                            val id = doc["__id"] as? String ?: continue
                            val pb = doc["photoBase64"] as? String
                            val pr = doc["photoUrl"] as? String
                            val updates = mutableMapOf<String, Any?>()
                            if (!pb.isNullOrBlank() && pr.isNullOrBlank()) {
                                // migrate base64 to storage
                                try {
                                    val base64 = if (pb.startsWith("data:")) pb.substringAfter(",") else pb
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    val filename = "teachers/$id/photo_${System.currentTimeMillis()}.jpg"
                                    val ref = storage.reference.child(filename)
                                    ref.putBytes(bytes).addOnSuccessListener {
                                        ref.downloadUrl.addOnSuccessListener { dl ->
                                            val url = dl.toString()
                                            repo.setDocumentFields("teachers", id, mapOf("photoUrl" to url))
                                            repo.deleteFields("teachers", id, listOf("photoBase64", "photo_base64"))
                                        }
                                    }
                                    updated++
                                } catch (_: Exception) { }
                            }
                            // updates performed in callbacks above
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                // students
                try {
                    val studentDocs = repo.getAllDocuments("students")
                    for (doc in studentDocs) {
                        try {
                            val id = doc["__id"] as? String ?: continue
                            val ab = doc["avatarBase64"] as? String
                            val ar = doc["avatarUrl"] as? String
                            val updates = mutableMapOf<String, Any?>()
                            if (!ab.isNullOrBlank() && ar.isNullOrBlank()) {
                                try {
                                    val base64 = if (ab.startsWith("data:")) ab.substringAfter(",") else ab
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    val filename = "students/$id/avatar_${System.currentTimeMillis()}.jpg"
                                    val ref = storage.reference.child(filename)
                                    ref.putBytes(bytes).addOnSuccessListener {
                                        ref.downloadUrl.addOnSuccessListener { dl ->
                                            val url = dl.toString()
                                            repo.setDocumentFields("students", id, mapOf("avatarUrl" to url, "avatar" to url))
                                            repo.deleteFields("students", id, listOf("avatarBase64", "avatar_base64"))
                                        }
                                    }
                                    updated++
                                } catch (_: Exception) { }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                callback("Backfill realizado: $updated documentos actualizados")
            } catch (e: Exception) {
                callback("Backfill fallido: ${e.message}")
            }
        }
    }

 }
