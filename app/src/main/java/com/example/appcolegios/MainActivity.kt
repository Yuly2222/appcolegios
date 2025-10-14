package com.example.appcolegios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.appcolegios.navigation.AppNavigation
import com.example.appcolegios.navigation.AppRoutes
import com.example.appcolegios.ui.theme.AppColegiosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkHost = intent?.data?.host
        val startDestination = when {
            intent.getStringExtra("startDestination") != null -> intent.getStringExtra("startDestination")!!
            deepLinkHost == "notifications" -> AppRoutes.Notifications.route
            else -> AppRoutes.Splash.route
        }
        setContent {
            AppColegiosTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = startDestination)
                }
            }
        }
    }
}