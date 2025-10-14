package com.example.appcolegios.demo

import com.example.appcolegios.data.model.Notification
import com.example.appcolegios.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.Date

object DemoData {
    const val DEMO_EMAIL = "jcamilodiaz7@gmail.com"

    fun isDemoUser(): Boolean =
        FirebaseAuth.getInstance().currentUser?.email?.equals(DEMO_EMAIL, ignoreCase = true) == true

    fun unreadNotificationsCount(): Int = 3
    fun unreadMessagesCount(): Int = 2

    fun demoNotifications(): Map<String, List<Notification>> {
        val now = Date()
        val cal = Calendar.getInstance()
        val todayList = listOf(
            Notification(
                id = "n1",
                titulo = "Cambio de aula",
                cuerpo = "La clase de Matemáticas se moverá al aula 302.",
                remitente = "Coordinación Académica",
                fechaHora = now,
                leida = false
            ),
            Notification(
                id = "n2",
                titulo = "Nueva tarea",
                cuerpo = "Sube tu informe de ciencias antes del viernes.",
                remitente = "Profe. Sandra",
                fechaHora = now,
                leida = false
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = cal.time
        val yesterdayList = listOf(
            Notification(
                id = "n3",
                titulo = "Recordatorio de pagos",
                cuerpo = "Tu pago del mes está pendiente.",
                remitente = "Tesorería",
                fechaHora = yesterday,
                leida = true
            )
        )
        return linkedMapOf(
            "Hoy" to todayList,
            "Ayer" to yesterdayList
        )
    }

    fun demoStudent(): Student = Student(
        id = "demo-student-1",
        nombre = "Juan Camilo Díaz",
        curso = "10°",
        grupo = "A",
        numeroLista = 12,
        correoInstitucional = DEMO_EMAIL,
        eps = "Sura",
        estadoMatricula = "Activa",
        promedio = 4.6,
        avatarUrl = null
    )
}

