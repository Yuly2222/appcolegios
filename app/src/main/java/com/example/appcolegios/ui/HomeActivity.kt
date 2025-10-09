package com.example.appcolegios.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.example.appcolegios.notificaciones.NotificationsActivity
import com.example.appcolegios.mensajes.ConversationsActivity
import com.example.appcolegios.pagos.PaymentsActivity
import com.example.appcolegios.transporte.TransportActivity
import com.example.appcolegios.perfil.AcademicInfoActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.btnGrades).setOnClickListener {
            startActivity(Intent(this, GradesActivity::class.java))
        }
        findViewById<Button>(R.id.btnAttendance).setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }
        findViewById<Button>(R.id.btnTasks).setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        findViewById<Button>(R.id.btnCalendar).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        findViewById<Button>(R.id.btnEvents).setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        findViewById<Button>(R.id.btnMessages).setOnClickListener {
            startActivity(Intent(this, ConversationsActivity::class.java))
        }
        findViewById<Button>(R.id.btnPayments).setOnClickListener {
            startActivity(Intent(this, PaymentsActivity::class.java))
        }
        findViewById<Button>(R.id.btnTransport).setOnClickListener {
            startActivity(Intent(this, TransportActivity::class.java))
        }
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnAcademicInfo).setOnClickListener {
            startActivity(Intent(this, AcademicInfoActivity::class.java))
        }
    }
}
