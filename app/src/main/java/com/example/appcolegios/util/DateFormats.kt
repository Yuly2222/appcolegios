package com.example.appcolegios.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormats {
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormatter12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
    fun formatDate(date: Date): String = dateFormatter.format(date)
    fun formatTime(date: Date): String = timeFormatter12.format(date)
}
