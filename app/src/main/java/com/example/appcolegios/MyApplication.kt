package com.example.appcolegios

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        // Forzar el uso de la Realtime Database indicada y habilitar persistencia
        try {
            val db = FirebaseDatabase.getInstance("https://appcolegios-a0c84-default-rtdb.firebaseio.com/")
            try {
                db.setPersistenceEnabled(true)
            } catch (_: Exception) {
                // Ignorar si ya fue habilitado previamente
            }
        } catch (_: Exception) {
            // Evitar crash en inicio si algo falla con Firebase
        }
    }
}
