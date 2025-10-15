package com.example.appcolegios.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.example.appcolegios.auth.LoginActivity
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class EventsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var chipToday: Chip
    private lateinit var chipWeek: Chip
    private lateinit var chipMonth: Chip

    private var currentFilter = "today"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupRecyclerView()
        setupFilters()
        loadEvents()
    }

    private fun initViews() {
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
        chipToday = findViewById(R.id.chipToday)
        chipWeek = findViewById(R.id.chipWeek)
        chipMonth = findViewById(R.id.chipMonth)
    }

    private fun setupRecyclerView() {
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        eventsRecyclerView.adapter = EventsAdapter(emptyList())
    }

    private fun setupFilters() {
        chipToday.setOnClickListener {
            currentFilter = "today"
            loadEvents()
        }

        chipWeek.setOnClickListener {
            currentFilter = "week"
            loadEvents()
        }

        chipMonth.setOnClickListener {
            currentFilter = "month"
            loadEvents()
        }
    }

    private fun loadEvents() {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            progressBar.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = getString(R.string.events_login_required)
            Snackbar.make(eventsRecyclerView, getString(R.string.events_login_snackbar), Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(R.string.action_login)) {
                    try {
                        startActivity(Intent(this, LoginActivity::class.java))
                    } catch (ex: Exception) {
                        Log.e("EventsActivity", "No se pudo abrir LoginActivity: ${ex.message}", ex)
                    }
                }
                .show()
            return
        }

        val calendar = Calendar.getInstance()
        // Compute start and end Date according to filter, using day boundaries
        val startCal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }

        when (currentFilter) {
            "week" -> {
                endCal.add(Calendar.DAY_OF_YEAR, 7)
            }
            "month" -> {
                endCal.add(Calendar.MONTH, 1)
            }
        }

        // Use Firebase Timestamps for range queries (field 'date' is stored as Timestamp)
        val startTs = com.google.firebase.Timestamp(startCal.time)
        val endTs = com.google.firebase.Timestamp(endCal.time)

        // Query user's events (subcollection) to respect security rules
        firestore.collection("users").document(currentUser.uid).collection("events")
            .whereGreaterThanOrEqualTo("date", startTs)
            .whereLessThanOrEqualTo("date", endTs)
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.text = getString(R.string.events_no_events)
                    (eventsRecyclerView.adapter as? EventsAdapter)?.update(emptyList())
                } else {
                    val list = documents.map { doc ->
                        val title = doc.getString("title") ?: doc.getString("titulo") ?: "Sin título"
                        val description = doc.getString("description") ?: doc.getString("descripcion") ?: ""
                        val dateField = doc.get("date")
                        val dateStr = when (dateField) {
                            is Timestamp -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateField.toDate())
                            is Date -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateField)
                            is String -> try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateField)?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: dateField } catch (_: Exception) { dateField }
                            else -> ""
                        }
                        EventItem(title = title, description = description, date = dateStr)
                    }
                    emptyStateText.visibility = View.GONE
                    (eventsRecyclerView.adapter as? EventsAdapter)?.update(list)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("EventsActivity", "Error loading events", e)
                val message = when (e) {
                    is FirebaseFirestoreException -> when (e.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> getString(R.string.events_error_permission)
                        else -> "Error al cargar eventos: ${e.message}"
                    }
                    else -> "Error al cargar eventos: ${e.message}"
                }
                Snackbar.make(eventsRecyclerView, message, Snackbar.LENGTH_LONG)
                    .setAction("Iniciar sesión") {
                        try { startActivity(Intent(this, LoginActivity::class.java)) } catch (_: Exception) {}
                    }
                    .show()
            }
    }

    // Simple data holder for adapter
    data class EventItem(val title: String, val description: String, val date: String)

    // Simple adapter using android.R.layout.simple_list_item_2
    inner class EventsAdapter(private var items: List<EventItem>) : RecyclerView.Adapter<EventsAdapter.VH>() {
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tv1: TextView = itemView.findViewById(android.R.id.text1)
            val tv2: TextView = itemView.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            holder.tv1.text = it.title
            holder.tv2.text = getString(R.string.events_item_format, it.date, it.description)
        }

        override fun getItemCount(): Int = items.size

        fun update(newItems: List<EventItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }
    }
}
