package com.example.appcolegios.data.model

data class ClassSession(
    var dayOfWeek: Int = 1, // 1 for Monday, 7 for Sunday; default 1
    var subject: String = "",
    var teacher: String = "",
    var startTime: String = "", // "HH:mm"
    var endTime: String = "", // "HH:mm"
    var classroom: String = ""
)
