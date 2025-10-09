package com.example.appcolegios.data.model

enum class Role {
    ESTUDIANTE, PADRE, DOCENTE;
    companion object {
        fun fromString(raw: String?): Role? = raw?.uppercase()?.let { value ->
            values().firstOrNull { it.name == value }
        }
    }
}
