package com.example.appcolegios.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoView: ImageView = findViewById(R.id.splashLogo)
        // Animación moderna: scale + fade
        val anim = AnimationUtils.loadAnimation(this, R.anim.splash_preload)
        logoView.startAnimation(anim)

        // Preload fijo de 3 segundos, luego ir a LoginActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val next = Intent(this, LoginActivity::class.java)
            // Preservar data (deep link) y extras si llegan
            intent?.data?.let { next.data = it }
            val extras = intent?.extras
            if (extras != null && !extras.isEmpty) {
                next.putExtras(extras)
            }
            startActivity(next)
            finish()
        }, 3000)
    }
}
