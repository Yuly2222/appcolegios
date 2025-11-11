package com.example.appcolegios.util

import com.google.firebase.firestore.DocumentSnapshot

object FirestoreUtils {
    /**
     * Devuelve el nombre preferido presente en el DocumentSnapshot.
     * Orden de preferencia: "nombre", "name", "displayName".
     */
    fun getPreferredName(doc: DocumentSnapshot?): String? {
        return doc?.getString("nombre") ?: doc?.getString("name") ?: doc?.getString("displayName")
    }
}

