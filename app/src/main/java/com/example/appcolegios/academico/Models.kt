package com.example.appcolegios.academico

// Definición centralizada de modelos usados en las pantallas académicas
data class CourseSimple(
    val id: String,
    val name: String,
    val students: List<StudentSimple> = emptyList()
)

data class StudentSimple(
    val id: String,
    val name: String
)

