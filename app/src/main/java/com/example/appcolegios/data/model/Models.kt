@file:Suppress("unused") // Suprime warnings de modelos aún no referenciados (Guardian, enums y constantes) conservándolos para futuras funcionalidades

package com.example.appcolegios.data.model

import java.util.Date

data class Student(
    val id: String,
    val nombre: String,
    val curso: String,
    val grupo: String,
    val numeroLista: Int,
    val correoInstitucional: String,
    val eps: String,
    val estadoMatricula: String,
    val promedio: Double,
    val avatarUrl: String? = null
)

data class Guardian(
    val id: String,
    val nombre: String,
    val telefono: String,
    val parentesco: String
)

data class Grade(
    val materiaId: String,
    val materia: String,
    val periodo: Int,
    val calificacion: Double,
    val ponderacion: Double
)

data class AttendanceEntry(
    val fecha: Date,
    val estado: AttendanceStatus
)

enum class AttendanceStatus {
    PRESENTE, AUSENTE, TARDE
}

data class Homework(
    val id: String,
    val materia: String,
    val titulo: String,
    val descripcion: String,
    val deadline: Date,
    val progreso: Float,
    val completada: Boolean
)

data class Notification(
    val id: String,
    val titulo: String,
    val cuerpo: String,
    val remitente: String,
    val fechaHora: Date,
    val avatarUrl: String? = null,
    val leida: Boolean
)

data class Message(
    val id: String,
    val fromId: String,
    val toId: String,
    val texto: String,
    val fechaHora: Date,
    val tipo: MessageType,
    val estado: MessageStatus
)

enum class MessageType {
    TEXTO, IMAGEN
}

enum class MessageStatus {
    ENVIADO, ENTREGADO, LEIDO
}

data class Event(
    val id: String,
    val titulo: String,
    val fechaHora: Date,
    val categoria: EventCategory,
    val descripcion: String,
    val icon: String? = null
)

enum class EventCategory {
    ACADEMICO, ADMINISTRATIVO
}

enum class TransportMode {
    A_PIE, BICICLETA, PUBLICO, PARTICULAR
}

enum class ArrivalStatus {
    EN_CAMINO, LLEGADO
}
