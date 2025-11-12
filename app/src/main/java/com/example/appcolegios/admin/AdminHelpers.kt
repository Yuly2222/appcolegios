package com.example.appcolegios.admin

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Helpers para administración: búsqueda de UID por identificador (email o uid)
 * y asignación de usuario (docente) a curso/grupo.
 */

object AdminHelpers {
    suspend fun findUidByIdentifier(db: FirebaseFirestore, identifier: String?): String? {
        val id = identifier?.trim() ?: return null
        if (id.isBlank()) return null

        try {
            if (id.contains("@")) {
                val email = id.lowercase()
                // Buscar en users
                val uq = db.collection("users").whereEqualTo("email", email).get().await()
                if (!uq.isEmpty) return uq.documents[0].id

                // Buscar en teachers
                val tq = db.collection("teachers").whereEqualTo("email", email).get().await()
                if (!tq.isEmpty) return tq.documents[0].id

                // Buscar en students
                val sq = db.collection("students").whereEqualTo("email", email).get().await()
                if (!sq.isEmpty) return sq.documents[0].id

                return null
            } else {
                // Podría ser un UID; comprobar existencia en collections comunes
                val uDoc = db.collection("users").document(id).get().await()
                if (uDoc.exists()) return id
                val tDoc = db.collection("teachers").document(id).get().await()
                if (tDoc.exists()) return id
                val sDoc = db.collection("students").document(id).get().await()
                if (sDoc.exists()) return id
                return null
            }
        } catch (e: Exception) {
            // En caso de error, devolver null para que el llamador gestione
            return null
        }
    }

    /**
     * Asigna (o crea cuando es posible) un docente a un curso/grupo.
     * Retorna true si se realizó alguna acción (usuario encontrado/creado y actualizado).
     */
    suspend fun assignToCourseAndGroup(db: FirebaseFirestore, identifier: String, curso: String, grupo: String): Boolean {
        val idTrim = identifier.trim()
        if (idTrim.isBlank()) return false

        var uid = findUidByIdentifier(db, idTrim)
        val isEmail = idTrim.contains("@")

        try {
            if (uid == null && isEmail) {
                // Crear usuario mínimo en users y teachers
                val newId = db.collection("users").document().id
                val baseUser = hashMapOf<String, Any?>("email" to idTrim.lowercase(), "createdAt" to Timestamp.now(), "role" to "teacher")
                try { db.collection("users").document(newId).set(baseUser).await() } catch (_: Exception) {}
                try { db.collection("teachers").document(newId).set(hashMapOf("email" to idTrim.lowercase(), "createdAt" to Timestamp.now())).await() } catch (_: Exception) {}
                uid = newId
            }

            if (uid == null) return false

            val courseNotBlank = curso.isNotBlank()
            val groupNotBlank = grupo.isNotBlank()
            val groupKey = if (courseNotBlank && groupNotBlank) "${curso.trim()}-${grupo.trim()}" else null

            // Actualizar teachers/{uid}
            try {
                val updates = mutableMapOf<String, Any?>("role" to "teacher")
                if (groupKey != null) {
                    updates["grupos"] = FieldValue.arrayUnion(groupKey)
                    updates["curso"] = groupKey
                    updates["grupo"] = grupo.trim()
                    updates["curso_simple"] = curso.trim()
                }
                db.collection("teachers").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                // intentar update si set falla por cualquier razón
                try {
                    if (groupKey != null) {
                        db.collection("teachers").document(uid).update(mapOf(
                            "grupos" to FieldValue.arrayUnion(groupKey),
                            "curso" to groupKey,
                            "grupo" to grupo.trim(),
                            "curso_simple" to curso.trim()
                        )).await()
                    }
                } catch (_: Exception) {}
            }

            // Actualizar users/{uid} (agregar los mismos campos de curso/grupo)
            try {
                val userUpdates = mutableMapOf<String, Any?>("role" to "teacher")
                if (groupKey != null) {
                    userUpdates["grupos"] = FieldValue.arrayUnion(groupKey)
                    userUpdates["curso"] = groupKey
                    userUpdates["grupo"] = grupo.trim()
                    userUpdates["curso_simple"] = curso.trim()
                    userUpdates["groupKey"] = groupKey
                }
                db.collection("users").document(uid).set(userUpdates, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (_: Exception) {}

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Actualiza el documento users/{uid} para el usuario identificado por email o uid,
     * añadiendo los campos de curso/grupo (grupos arrayUnion, curso, grupo, curso_simple, groupKey).
     */
    suspend fun updateUserDocCourse(db: FirebaseFirestore, identifier: String, curso: String, grupo: String): Boolean {
        val idTrim = identifier.trim()
        if (idTrim.isBlank()) return false

        val uid = findUidByIdentifier(db, idTrim) ?: return false

        val courseNotBlank = curso.isNotBlank()
        val groupNotBlank = grupo.isNotBlank()
        val groupKey = if (courseNotBlank && groupNotBlank) "${curso.trim()}-${grupo.trim()}" else null

        try {
            val updates = mutableMapOf<String, Any?>()
            if (groupKey != null) {
                updates["grupos"] = FieldValue.arrayUnion(groupKey)
                updates["curso"] = groupKey
                updates["grupo"] = grupo.trim()
                updates["curso_simple"] = curso.trim()
                updates["groupKey"] = groupKey
            }
            // Además, asegurarnos de marcar role si no existe
            updates.putIfAbsent("role", "user")

            if (updates.isNotEmpty()) {
                db.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
