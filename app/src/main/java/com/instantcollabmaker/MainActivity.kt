package com.instantcollabmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.instantcollabmaker.navigation.AppNavigation
import com.instantcollabmaker.ui.theme.FrameTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameTraceTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
