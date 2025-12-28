package com.example.rpapp3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.rpapp3.ui.navigation.AppNavigation
import com.example.rpapp3.ui.theme.RPApp3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPApp3Theme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}