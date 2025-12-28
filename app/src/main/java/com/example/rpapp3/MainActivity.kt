package com.example.rpapp3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.rpapp3.ui.navigation.AppNavigation
import com.example.rpapp3.ui.theme.RPApp3Theme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeManager = com.example.rpapp3.data.ThemeManager.getInstance(this)
        
        setContent {
            val currentTheme = themeManager.currentTheme.collectAsState(initial = com.example.rpapp3.data.ThemeManager.THEME_MODERN_DARK).value
            
            RPApp3Theme(selectedTheme = currentTheme) {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}