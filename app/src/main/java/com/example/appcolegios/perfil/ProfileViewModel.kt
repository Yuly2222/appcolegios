package com.example.appcolegios.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _student = MutableStateFlow<Result<Student?>?>(null)
    val student: StateFlow<Result<Student?>?> = _student

    init {
        loadStudentData()
    }

    private fun loadStudentData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val document = db.collection("students").document(userId).get().await()
                    val studentData = document.toObject(Student::class.java)
                    _student.value = Result.success(studentData)
                } catch (e: Exception) {
                    _student.value = Result.failure(e)
                }
            }
        }
    }
}

