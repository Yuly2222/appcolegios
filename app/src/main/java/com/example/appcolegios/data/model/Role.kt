package com.example.appcolegios.data.model

import java.util.Locale

enum class Role {
    ESTUDIANTE, PADRE, DOCENTE, ADMIN;
    companion object {
        fun fromString(raw: String?): Role? = raw?.uppercase(Locale.ROOT)?.let { value ->
            entries.firstOrNull { it.name == value }
        }
    }
}
