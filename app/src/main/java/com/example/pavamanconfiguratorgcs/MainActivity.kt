package com.example.pavamanconfiguratorgcs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.pavamanconfiguratorgcs.navigation.AppNavigation
import com.example.pavamanconfiguratorgcs.ui.theme.PavamanConfiguratorGCSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PavamanConfiguratorGCSTheme {
                AppNavigation(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
