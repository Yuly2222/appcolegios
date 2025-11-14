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
import kotlinx.coroutines.flow.asStateFlow
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
    private val storage = FirebaseStorage.getInstance()

    private val _student = MutableStateFlow<Result<Student?>?>(null)
    val student: StateFlow<Result<Student?>?> = _student

    private val _roleString = MutableStateFlow<String?>(null)
    val roleString: StateFlow<String?> = _roleString

    private val _children = MutableStateFlow<List<Student>>(emptyList())
    val children = _children.asStateFlow()
    private val _selectedChildIndex = MutableStateFlow<Int?>(null)
    val selectedChildIndex = _selectedChildIndex.asStateFlow()
    private val _teacherState = MutableStateFlow<Result<TeacherProfile?>?>(null)
    val teacherState = _teacherState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) {
            loadStudentData()
            loadRoleFromDb()
            loadChildrenForParent()
        } else {
            _student.value = Result.success(null)
            _roleString.value = null
        }
    }

    fun selectChildAtIndex(index: Int) {
        val list = _children.value
        if (index in list.indices) {
            _selectedChildIndex.value = index
            _student.value = Result.success(list[index])
        }
    }

    // Cargar hijos asociados (simplificado: buscar en students por acudienteId == uid)
    private fun loadChildrenForParent() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                val snaps = db.collection("students").whereEqualTo("acudienteId", uid).get().await()
                val mapped = snaps.documents.mapNotNull { it.toObject(Student::class.java)?.copy(id = it.id) }
                _children.value = mapped
                if (mapped.isNotEmpty() && _selectedChildIndex.value == null) _selectedChildIndex.value = 0
            } catch (_: Exception) { }
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        if (auth.currentUser != null) {
            loadStudentData()
            loadRoleFromDb()
            loadChildrenForParent()
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _student.value = Result.success(null)
                return@launch
            }

            try {
                val studentDocRef = db.collection("students").document(userId)
                val studentDoc = studentDocRef.get().await()

                if (studentDoc.exists()) {
                    val studentData = studentDoc.toObject(Student::class.java)
                    _student.value = Result.success(studentData)
                } else {
                    // Si no existe, podemos crear un perfil vacío para el usuario
                    val newStudent = Student(id = userId, correoInstitucional = auth.currentUser?.email ?: "")
                    studentDocRef.set(newStudent).await()
                    _student.value = Result.success(newStudent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading student data", e)
                _student.value = Result.failure(e)
            }
        }
    }

    fun uploadProfileImage(uri: Uri) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            val storageRef = storage.reference.child("profile_images/$userId.jpg")

            try {
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                db.collection("students").document(userId)
                    .update("photoUrl", downloadUrl)
                    .await()
                // Recargar los datos para que la UI se actualice con la nueva foto
                loadStudentData()
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading profile image", e)
                // Opcional: notificar a la UI sobre el error
            }
        }
    }

    private fun loadRoleFromDb() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                _roleString.value = null
                return@launch
            }
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    _roleString.value = doc.getString("role")
                } else {
                    // Fallback a la colección de estudiantes si no está en users
                    val studentDoc = db.collection("students").document(uid).get().await()
                    if (studentDoc.exists()) {
                        _roleString.value = "ESTUDIANTE"
                    } else {
                        _roleString.value = null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadRoleFromDb: error", e)
                _roleString.value = null
            }
        }
    }

    fun refreshAllData() {
        loadStudentData()
        loadRoleFromDb()
    }

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun uploadStudentPhotoAsBase64WithResolver(resolver: ContentResolver, uri: Uri, callback: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: run {
                    callback(null, "No se pudo leer archivo")
                    return@launch
                }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val uid = auth.currentUser?.uid ?: run {
                    callback(null, "Sin usuario")
                    return@launch
                }
                val updates = mapOf("photoBase64" to base64)
                db.collection("students").document(uid).set(updates, SetOptions.merge()).await()
                loadStudentData()
                callback("data:image/jpeg;base64,$base64", null)
            } catch (e: Exception) {
                callback(null, e.message)
            }
        }
    }

    fun saveTeacherProfile(name: String?, phone: String?, photoUrl: String?) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val updates = mutableMapOf<String, Any?>()
            if (!name.isNullOrBlank()) updates["name"] = name
            if (!phone.isNullOrBlank()) updates["phone"] = phone
            if (!photoUrl.isNullOrBlank()) updates["photoUrl"] = photoUrl
            if (updates.isNotEmpty()) {
                db.collection("teachers").document(uid).set(updates, SetOptions.merge()).await()
                _teacherState.value = Result.success(TeacherProfile(nombre = name, email = getCurrentUserEmail(), phone = phone, photoUrl = photoUrl))
            }
        }
    }

    fun uploadPhotoAsBase64WithResolver(resolver: ContentResolver, uri: Uri, callback: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: run {
                    callback(null, "No se pudo leer archivo")
                    return@launch
                }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUri = "data:image/jpeg;base64,$base64"
                val uid = auth.currentUser?.uid ?: run {
                    callback(null, "Sin usuario")
                    return@launch
                }
                val updates = mapOf("photoBase64" to base64, "photoUrl" to dataUri)
                db.collection("teachers").document(uid).set(updates, SetOptions.merge()).await()
                _teacherState.value?.getOrNull()?.let { teacher ->
                    _teacherState.value = Result.success(teacher.copy(photoUrl = dataUri))
                }
                callback(dataUri, null)
            } catch (e: Exception) {
                callback(null, e.message)
            }
        }
    }
}
