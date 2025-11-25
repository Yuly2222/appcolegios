package com.example.appcolegios.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.ClassSession
import com.example.appcolegios.perfil.ProfileViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import java.util.*

class ScheduleActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var profileVm: ProfileViewModel

    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var currentDayText: TextView
    private lateinit var previousDayButton: Button
    private lateinit var nextDayButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var scheduleAdapter: ScheduleAdapter

    private var currentDay = Calendar.getInstance()
    private val daysOfWeek = arrayOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userPrefs = UserPreferencesRepository(this)
        profileVm = ViewModelProvider(this).get(ProfileViewModel::class.java)

        initViews()
        setupRecyclerView()
        setupListeners()
        // Inicializar carga: cargaremos según si el usuario es estudiante o padre (hijo seleccionado)
        // También soportamos cambio de día con los botones
        // Cargar inicialmente
        loadSchedule()
    }

    private fun initViews() {
        scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView)
        currentDayText = findViewById(R.id.currentDayText)
        previousDayButton = findViewById(R.id.previousDayButton)
        nextDayButton = findViewById(R.id.nextDayButton)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)

        updateDayText()
    }

    private fun setupRecyclerView() {
        scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter(emptyList())
        scheduleRecyclerView.adapter = scheduleAdapter
    }

    private fun setupListeners() {
        previousDayButton.setOnClickListener {
            currentDay.add(Calendar.DAY_OF_WEEK, -1)
            updateDayText()
            loadSchedule()
        }

        nextDayButton.setOnClickListener {
            currentDay.add(Calendar.DAY_OF_WEEK, 1)
            updateDayText()
            loadSchedule()
        }
    }

    private fun updateDayText() {
        val dayOfWeek = currentDay.get(Calendar.DAY_OF_WEEK)
        currentDayText.text = daysOfWeek[dayOfWeek - 1]
    }

    private fun loadSchedule() {
        // Determinar uid a cargar: si el usuario tiene hijos (es padre) y existe un seleccionado, usamos el id del hijo;
        // en caso contrario usamos el uid del usuario autenticado (estudiante)
        val uidToLoad = resolveUidForSchedule() ?: run { Snackbar.make(scheduleRecyclerView, "Usuario no identificado", Snackbar.LENGTH_LONG).show(); return }
        loadScheduleFor(uidToLoad)
    }

    private fun resolveUidForSchedule(): String? {
        // Preferir hijo seleccionado si hay hijos cargados en ProfileViewModel
        return try {
            val selectedIndex = profileVm.selectedChildIndex.value
            val children = profileVm.children.value
            if (selectedIndex != null && children.isNotEmpty() && selectedIndex >= 0 && selectedIndex < children.size) {
                children[selectedIndex].id
            } else {
                // Fallback: uid del usuario autenticado
                auth.currentUser?.uid
            }
        } catch (e: Exception) {
            auth.currentUser?.uid
        }
    }

    private fun loadScheduleFor(uid: String) {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        // Calcular índice de día (ClassSession dayOfWeek usa 1=Monday..7=Sunday)
        val dow = currentDay.get(Calendar.DAY_OF_WEEK)
        val dayIndex = if (dow == Calendar.SUNDAY) 7 else dow - 1

        // Leer exactamente desde students/{uid}/schedule
        firestore.collection("students").document(uid).collection("schedule")
            .get()
            .addOnSuccessListener { snaps ->
                progressBar.visibility = View.GONE
                val list = mutableListOf<ClassSession>()
                for (doc in snaps.documents) {
                    try {
                        val cs = doc.toObject(ClassSession::class.java)
                        if (cs != null && cs.dayOfWeek == dayIndex) list.add(cs)
                    } catch (_: Exception) { }
                }
                if (list.isEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                    scheduleAdapter.submitList(emptyList())
                } else {
                    // ordenar por startTime
                    val sorted = list.sortedWith(compareBy({ it.startTime }))
                    scheduleAdapter.submitList(sorted)
                    emptyStateText.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(scheduleRecyclerView, "Error al cargar horario: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadScheduleFor(uid) }
                    .show()
            }
    }
}
