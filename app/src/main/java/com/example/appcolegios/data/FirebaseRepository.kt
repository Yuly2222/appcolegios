package com.example.appcolegios.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Devuelve una lista de mapas con keys: id, displayName, email, role
    suspend fun fetchAdminUserList(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val studentsSnap = db.collection("students").get().await()
            if (!studentsSnap.isEmpty) {
                for (doc in studentsSnap.documents) {
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    val uid = doc.id
                    val normalized = normalizeDisplayForDoc(uid, map)
                    list.add(normalized.toMutableMap())
                }
            } else {
                val usersSnap = db.collection("users").get().await()
                for (doc in usersSnap.documents) {
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    val uid = doc.id
                    val normalized = normalizeDisplayForDoc(uid, map, isUser = true)
                    list.add(normalized.toMutableMap())
                }
            }
        } catch (_: Exception) {
            // retornar lo que tengamos (vacío si falla)
        }
        return list
    }

    private suspend fun normalizeDisplayForDoc(uid: String, sourceMap: MutableMap<String, Any?>, isUser: Boolean = false): Map<String, Any> {
        val out = mutableMapOf<String, Any>()
        // intentamos obtener displayName desde users/{uid} si existe y no estamos ya leyendo users
        if (!isUser) {
            try {
                val udoc = db.collection("users").document(uid).get().await()
                if (udoc.exists()) {
                    val dn = udoc.getString("displayName") ?: udoc.getString("name")
                    if (!dn.isNullOrBlank()) {
                        out["displayName"] = dn
                    }
                }
            } catch (_: Exception) {}
        }

        if (out["displayName"] == null) {
            val nombres = (sourceMap["nombres"] as? String) ?: (sourceMap["nombre"] as? String) ?: (sourceMap["name"] as? String)
            val apellidos = (sourceMap["apellidos"] as? String) ?: (sourceMap["lastname"] as? String) ?: ""
            val built = listOfNotNull(nombres?.takeIf { it.isNotBlank() }, apellidos.takeIf { it.isNotBlank() }).joinToString(" ")
            out["displayName"] = if (built.isBlank()) "(sin nombre)" else built
        }

        out["id"] = uid
        out["email"] = (sourceMap["email"] as? String) ?: (sourceMap["correo"] as? String) ?: ""
        // role: si está en el source map, usarlo; si no, inferir "ESTUDIANTE" cuando estamos leyendo students
        out["role"] = (sourceMap["role"] as? String) ?: (if (!isUser) "ESTUDIANTE" else "")
        return out
    }
}

