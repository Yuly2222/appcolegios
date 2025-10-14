package com.example.appcolegios.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Clase para inicializar datos de prueba en Firestore
 * Usuarios de prueba:
 * - Estudiante: jcamilodiaz7@gmail.com
 * - Profesor: hermanitos605@gmail.com
 * - Admin: jcamilodiaz777@gmail.com
 * - Padre: hermanitos604@gmail.com
 */
object TestDataInitializer {

    suspend fun initializeAllTestData() {
        val firestore = FirebaseFirestore.getInstance()
        try {
            // Crear datos del estudiante
            createStudentData(firestore)

            // Crear datos del profesor
            createTeacherData(firestore)

            // Crear datos del admin
            createAdminData(firestore)

            // Crear datos del padre
            createParentData(firestore)

            // Crear datos compartidos (grupos, materias, etc.)
            createSharedData(firestore)

            println("✅ Datos de prueba inicializados correctamente")
        } catch (e: Exception) {
            println("❌ Error inicializando datos: ${e.message}")
        }
    }

    private suspend fun createStudentData(firestore: FirebaseFirestore) {
        val studentId = "student_jcamilo"
        val studentEmail = "jcamilodiaz7@gmail.com"

        // Datos básicos del estudiante
        val studentData = hashMapOf(
            "id" to studentId,
            "email" to studentEmail,
            "nombre" to "Juan Camilo Díaz",
            "rol" to "STUDENT",
            "curso" to "10-A",
            "identificacion" to "1234567890",
            "fechaNacimiento" to "2008-05-15",
            "direccion" to "Calle 123 #45-67",
            "telefono" to "3001234567",
            "acudienteId" to "parent_herman604",
            "createdAt" to Date()
        )

        firestore.collection("users").document(studentId).set(studentData).await()
        firestore.collection("students").document(studentId).set(studentData).await()

        // Notas del estudiante
        val notas = listOf(
            hashMapOf(
                "materia" to "Matemáticas",
                "periodo" to "1",
                "nota" to 4.5,
                "observaciones" to "Excelente desempeño en cálculo",
                "fecha" to Date()
            ),
            hashMapOf(
                "materia" to "Español",
                "periodo" to "1",
                "nota" to 4.2,
                "observaciones" to "Buena comprensión lectora",
                "fecha" to Date()
            ),
            hashMapOf(
                "materia" to "Ciencias",
                "periodo" to "1",
                "nota" to 4.8,
                "observaciones" to "Sobresaliente en laboratorios",
                "fecha" to Date()
            ),
            hashMapOf(
                "materia" to "Inglés",
                "periodo" to "1",
                "nota" to 4.0,
                "observaciones" to "Buen nivel conversacional",
                "fecha" to Date()
            )
        )

        notas.forEach { nota ->
            firestore.collection("students").document(studentId)
                .collection("notas").add(nota).await()
        }

        // Asistencia del estudiante
        val asistencias = listOf(
            hashMapOf(
                "fecha" to "2025-01-10",
                "estado" to "PRESENTE",
                "observaciones" to ""
            ),
            hashMapOf(
                "fecha" to "2025-01-11",
                "estado" to "PRESENTE",
                "observaciones" to ""
            ),
            hashMapOf(
                "fecha" to "2025-01-12",
                "estado" to "AUSENTE",
                "observaciones" to "Cita médica justificada"
            ),
            hashMapOf(
                "fecha" to "2025-01-13",
                "estado" to "PRESENTE",
                "observaciones" to ""
            )
        )

        asistencias.forEach { asistencia ->
            firestore.collection("students").document(studentId)
                .collection("asistencia").add(asistencia).await()
        }

        // Tareas del estudiante
        val tareas = listOf(
            hashMapOf(
                "titulo" to "Ejercicios de Álgebra",
                "descripcion" to "Resolver ejercicios del capítulo 5, páginas 45-50",
                "materia" to "Matemáticas",
                "fechaEntrega" to "2025-01-20",
                "estado" to "PENDIENTE",
                "prioridad" to "ALTA"
            ),
            hashMapOf(
                "titulo" to "Ensayo sobre El Quijote",
                "descripcion" to "Escribir un ensayo de 500 palabras sobre los personajes principales",
                "materia" to "Español",
                "fechaEntrega" to "2025-01-22",
                "estado" to "EN_PROGRESO",
                "prioridad" to "MEDIA"
            ),
            hashMapOf(
                "titulo" to "Informe de Laboratorio",
                "descripcion" to "Documentar experimento sobre reacciones químicas",
                "materia" to "Ciencias",
                "fechaEntrega" to "2025-01-18",
                "estado" to "COMPLETADA",
                "prioridad" to "ALTA",
                "calificacion" to 4.7
            )
        )

        tareas.forEach { tarea ->
            firestore.collection("students").document(studentId)
                .collection("tareas").add(tarea).await()
        }

        // Horario del estudiante
        val horario = listOf(
            hashMapOf(
                "dia" to "Lunes",
                "hora" to "07:00 - 08:00",
                "materia" to "Matemáticas",
                "salon" to "A-201"
            ),
            hashMapOf(
                "dia" to "Lunes",
                "hora" to "08:00 - 09:00",
                "materia" to "Español",
                "salon" to "A-105"
            ),
            hashMapOf(
                "dia" to "Martes",
                "hora" to "07:00 - 08:00",
                "materia" to "Ciencias",
                "salon" to "LAB-01"
            ),
            hashMapOf(
                "dia" to "Martes",
                "hora" to "08:00 - 09:00",
                "materia" to "Inglés",
                "salon" to "B-302"
            )
        )

        horario.forEach { clase ->
            firestore.collection("students").document(studentId)
                .collection("horario").add(clase).await()
        }

        // Recursos de estudio
        val recursos = listOf(
            hashMapOf(
                "titulo" to "Guía de Álgebra Avanzada",
                "tipo" to "PDF",
                "materia" to "Matemáticas",
                "url" to "https://ejemplo.com/algebra.pdf",
                "fecha" to Date()
            ),
            hashMapOf(
                "titulo" to "Video: El Quijote Explicado",
                "tipo" to "VIDEO",
                "materia" to "Español",
                "url" to "https://ejemplo.com/quijote-video",
                "fecha" to Date()
            )
        )

        recursos.forEach { recurso ->
            firestore.collection("students").document(studentId)
                .collection("recursos").add(recurso).await()
        }
    }

    private suspend fun createTeacherData(firestore: FirebaseFirestore) {
        val teacherId = "teacher_herman605"
        val teacherEmail = "hermanitos605@gmail.com"

        val teacherData = hashMapOf(
            "id" to teacherId,
            "email" to teacherEmail,
            "nombre" to "Herman González",
            "rol" to "TEACHER",
            "materias" to listOf("Matemáticas", "Física"),
            "cursos" to listOf("10-A", "10-B", "11-A"),
            "identificacion" to "9876543210",
            "telefono" to "3009876543",
            "especialidad" to "Ciencias Exactas",
            "createdAt" to Date()
        )

        firestore.collection("users").document(teacherId).set(teacherData).await()
        firestore.collection("teachers").document(teacherId).set(teacherData).await()

        // Grupos asignados al profesor
        val grupos = listOf(
            hashMapOf(
                "curso" to "10-A",
                "materia" to "Matemáticas",
                "estudiantes" to 32,
                "horario" to "Lunes y Miércoles 07:00-09:00"
            ),
            hashMapOf(
                "curso" to "10-B",
                "materia" to "Matemáticas",
                "estudiantes" to 28,
                "horario" to "Martes y Jueves 08:00-10:00"
            )
        )

        grupos.forEach { grupo ->
            firestore.collection("teachers").document(teacherId)
                .collection("grupos").add(grupo).await()
        }

        // Tareas publicadas por el profesor
        val tareasPublicadas = listOf(
            hashMapOf(
                "titulo" to "Ejercicios de Álgebra",
                "descripcion" to "Resolver ejercicios del capítulo 5",
                "materia" to "Matemáticas",
                "curso" to "10-A",
                "fechaPublicacion" to Date(),
                "fechaEntrega" to "2025-01-20",
                "valor" to 10.0
            )
        )

        tareasPublicadas.forEach { tarea ->
            firestore.collection("teachers").document(teacherId)
                .collection("tareas_publicadas").add(tarea).await()
        }
    }

    private suspend fun createAdminData(firestore: FirebaseFirestore) {
        val adminId = "admin_jcamilo777"
        val adminEmail = "jcamilodiaz777@gmail.com"

        val adminData = hashMapOf(
            "id" to adminId,
            "email" to adminEmail,
            "nombre" to "Juan Camilo Díaz (Admin)",
            "rol" to "ADMIN",
            "cargo" to "Rector",
            "permisos" to listOf("CREAR_USUARIOS", "GESTIONAR_CURSOS", "VER_REPORTES", "CONFIGURAR_SISTEMA"),
            "identificacion" to "1122334455",
            "telefono" to "3001122334",
            "createdAt" to Date()
        )

        firestore.collection("users").document(adminId).set(adminData).await()
        firestore.collection("admins").document(adminId).set(adminData).await()
    }

    private suspend fun createParentData(firestore: FirebaseFirestore) {
        val parentId = "parent_herman604"
        val parentEmail = "hermanitos604@gmail.com"

        val parentData = hashMapOf(
            "id" to parentId,
            "email" to parentEmail,
            "nombre" to "Herman Díaz (Padre)",
            "rol" to "PARENT",
            "hijosIds" to listOf("student_jcamilo"),
            "identificacion" to "5566778899",
            "telefono" to "3005566778",
            "direccion" to "Calle 123 #45-67",
            "createdAt" to Date()
        )

        firestore.collection("users").document(parentId).set(parentData).await()
        firestore.collection("parents").document(parentId).set(parentData).await()

        // Pagos del padre
        val pagos = listOf(
            hashMapOf(
                "concepto" to "Matrícula 2025",
                "monto" to 1500000.0,
                "fechaVencimiento" to "2025-02-01",
                "estado" to "PAGADO",
                "fechaPago" to "2025-01-15",
                "metodoPago" to "Transferencia"
            ),
            hashMapOf(
                "concepto" to "Pensión Enero",
                "monto" to 500000.0,
                "fechaVencimiento" to "2025-01-31",
                "estado" to "PAGADO",
                "fechaPago" to "2025-01-10",
                "metodoPago" to "Efectivo"
            ),
            hashMapOf(
                "concepto" to "Pensión Febrero",
                "monto" to 500000.0,
                "fechaVencimiento" to "2025-02-28",
                "estado" to "PENDIENTE",
                "metodoPago" to null
            )
        )

        pagos.forEach { pago ->
            firestore.collection("parents").document(parentId)
                .collection("pagos").add(pago).await()
        }

        // Notificaciones para el padre
        val notificaciones = listOf(
            hashMapOf(
                "titulo" to "Reunión de padres",
                "mensaje" to "Reunión este viernes 20 de enero a las 6:00 PM",
                "tipo" to "EVENTO",
                "fecha" to Date(),
                "leido" to false
            ),
            hashMapOf(
                "titulo" to "Calificaciones disponibles",
                "mensaje" to "Las notas del primer periodo ya están disponibles",
                "tipo" to "ACADEMICO",
                "fecha" to Date(),
                "leido" to false
            )
        )

        notificaciones.forEach { notif ->
            firestore.collection("parents").document(parentId)
                .collection("notificaciones").add(notif).await()
        }
    }

    private suspend fun createSharedData(firestore: FirebaseFirestore) {
        // Crear grupos/cursos
        val grupos = listOf(
            hashMapOf(
                "id" to "10-A",
                "nombre" to "Décimo A",
                "grado" to 10,
                "seccion" to "A",
                "directorGrupo" to "teacher_herman605",
                "estudiantesCount" to 32
            ),
            hashMapOf(
                "id" to "10-B",
                "nombre" to "Décimo B",
                "grado" to 10,
                "seccion" to "B",
                "directorGrupo" to "teacher_herman605",
                "estudiantesCount" to 28
            )
        )

        grupos.forEach { grupo ->
            firestore.collection("grupos").document(grupo["id"] as String).set(grupo).await()
        }

        // Crear materias
        val materias = listOf(
            hashMapOf(
                "id" to "mat_matematicas",
                "nombre" to "Matemáticas",
                "area" to "Ciencias Exactas",
                "intensidadHoraria" to 5
            ),
            hashMapOf(
                "id" to "mat_espanol",
                "nombre" to "Español",
                "area" to "Humanidades",
                "intensidadHoraria" to 4
            ),
            hashMapOf(
                "id" to "mat_ciencias",
                "nombre" to "Ciencias",
                "area" to "Ciencias Naturales",
                "intensidadHoraria" to 4
            ),
            hashMapOf(
                "id" to "mat_ingles",
                "nombre" to "Inglés",
                "area" to "Idiomas",
                "intensidadHoraria" to 3
            )
        )

        materias.forEach { materia ->
            firestore.collection("materias").document(materia["id"] as String).set(materia).await()
        }

        // Crear eventos institucionales
        val eventos = listOf(
            hashMapOf(
                "titulo" to "Reunión de padres",
                "descripcion" to "Reunión informativa del primer periodo académico",
                "fecha" to "2025-01-20",
                "hora" to "18:00",
                "lugar" to "Auditorio Principal",
                "tipo" to "REUNION"
            ),
            hashMapOf(
                "titulo" to "Feria de Ciencias",
                "descripcion" to "Exposición de proyectos científicos de los estudiantes",
                "fecha" to "2025-02-15",
                "hora" to "09:00",
                "lugar" to "Patio Central",
                "tipo" to "EVENTO"
            )
        )

        eventos.forEach { evento ->
            firestore.collection("eventos").add(evento).await()
        }
    }
}
