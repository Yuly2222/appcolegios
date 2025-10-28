package com.example.appcolegios.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Modelo simple para perfil de docente
data class TeacherProfile(
    val nombre: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null
)

class ProfileViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _student = MutableStateFlow<Result<Student?>?>(null)
    val student: StateFlow<Result<Student?>?> = _student

    // Estado para profesor
    private val _teacherState = MutableStateFlow<Result<TeacherProfile?>?>(null)
    val teacherState: StateFlow<Result<TeacherProfile?>?> = _teacherState

    init {
        loadStudentData()
        loadTeacherData()
    }

    private fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val document = db.collection("students").document(userId).get().await()
                    val studentData = document.toObject(Student::class.java) ?: Student()
                    _student.value = Result.success(studentData)
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
                // Intentar colección 'teachers' -> doc uid
                val doc = db.collection("teachers").document(userId).get().await()
                if (doc.exists()) {
                    val nombre = doc.getString("name") ?: doc.getString("displayName")
                    val email = doc.getString("email") ?: auth.currentUser?.email
                    val phone = doc.getString("phone")
                    val photo = doc.getString("photoUrl") ?: doc.getString("avatar")
                    _teacherState.value = Result.success(TeacherProfile(nombre, email, phone, photo))
                    return@launch
                }

                // Fallback: leer collection 'users'
                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    val nombre = userDoc.getString("displayName") ?: userDoc.getString("name")
                    val email = userDoc.getString("email") ?: auth.currentUser?.email
                    val phone = userDoc.getString("phone")
                    val photo = userDoc.getString("photoUrl") ?: userDoc.getString("avatar")
                    _teacherState.value = Result.success(TeacherProfile(nombre, email, phone, photo))
                    return@launch
                }

                // No existe documento: devolver null para que la UI muestre campos vacíos
                _teacherState.value = Result.success(null)
            } catch (e: Exception) {
                _teacherState.value = Result.failure(e)
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

    // Subir foto a Firebase Storage y devolver URL.
    fun uploadPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null); return@launch }
            try {
                val ref = FirebaseStorage.getInstance().reference.child("avatars/$userId.jpg")
                val downloadUrl = suspendCancellableCoroutine<String?> { cont ->
                    val uploadTask = ref.putFile(uri)
                    uploadTask.addOnSuccessListener {
                        ref.downloadUrl.addOnSuccessListener { uri2 -> cont.resume(uri2.toString()) }
                            .addOnFailureListener { cont.resume(null) }
                    }.addOnFailureListener {
                        cont.resume(null)
                    }
                }
                if (downloadUrl != null) {
                    // actualizar documento teachers/users con el nuevo photoUrl
                    saveTeacherProfile(_teacherState.value?.getOrNull()?.nombre ?: auth.currentUser?.displayName, _teacherState.value?.getOrNull()?.phone, downloadUrl)
                }
                onResult(downloadUrl)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    // Subir foto para estudiante y actualizar en 'students' y 'users'
    fun uploadStudentPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) { onResult(null); return@launch }
            try {
                val ref = FirebaseStorage.getInstance().reference.child("avatars/$userId.jpg")
                val downloadUrl = suspendCancellableCoroutine<String?> { cont ->
                    val uploadTask = ref.putFile(uri)
                    uploadTask.addOnSuccessListener {
                        ref.downloadUrl.addOnSuccessListener { uri2 -> cont.resume(uri2.toString()) }
                            .addOnFailureListener { cont.resume(null) }
                    }.addOnFailureListener {
                        cont.resume(null)
                    }
                }
                if (downloadUrl != null) {
                    // actualizar documento students y users
                    try {
                        db.collection("students").document(userId).set(mapOf("avatarUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (_: Exception) {}
                    try {
                        db.collection("users").document(userId).set(mapOf("avatarUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (_: Exception) {}
                }
                onResult(downloadUrl)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    // Helper público para obtener el email current del usuario (UI lo consulta)
    fun getCurrentUserEmail(): String? = auth.currentUser?.email
}
