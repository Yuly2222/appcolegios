package com.example.appcolegios.data.model

data class ClassSession(
    val dayOfWeek: Int, // 1 for Monday, 7 for Sunday
    val subject: String,
    val teacher: String,
    val startTime: String, // "HH:mm"
    val endTime: String, // "HH:mm"
    val classroom: String
)

