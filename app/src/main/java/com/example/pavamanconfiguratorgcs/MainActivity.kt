package com.example.pavamanconfiguratorgcs

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.pavamanconfiguratorgcs.navigation.AppNavigation
import com.example.pavamanconfiguratorgcs.ui.theme.PavamanConfiguratorGCSTheme

class MainActivity : ComponentActivity() {

    private val hasRequiredPermissions = mutableStateOf(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val bluetoothScanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false)
        } else {
            true // Not required for older APIs
        }

        val bluetoothConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        } else {
            true // Not required for older APIs
        }

        // Update permission state
        hasRequiredPermissions.value = bluetoothScanGranted && bluetoothConnectGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PavamanConfiguratorGCSTheme {
                AppNavigation(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        askForPermissions()
    }

    private fun askForPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
