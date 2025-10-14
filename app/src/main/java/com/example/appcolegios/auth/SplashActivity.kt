package com.example.appcolegios.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animación de fade in para el logo
        val logoView: ImageView = findViewById(R.id.splashLogo)
        val fadeIn: Animation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logoView.startAnimation(fadeIn)

        // Al terminar la animación, iniciar LoginActivity
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // Retardo breve antes de iniciar
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this@SplashActivity, com.example.appcolegios.auth.LoginActivity::class.java))
                    finish()
                }, 300)
            }
        })
    }
}
