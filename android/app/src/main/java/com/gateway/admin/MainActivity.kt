package com.gateway.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for real-time sound alarms", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        prefillServerUrl()

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                SettingsScreen(
                    viewModel = viewModel,
                    onRequestIgnoreBatteryOptimizations = ::requestIgnoreBatteryOptimizations
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun prefillServerUrl() {
        val prefs = getSharedPreferences("gateway_monitor_prefs", Context.MODE_PRIVATE)
        val currentUrl = prefs.getString("pref_base_url", "") ?: ""
        
        // Remove the default URL and keep it blank so users can put their own.
        // If current URL contains the old default run.app URL or is example.com, we reset it to blank.
        val isOldDefault = currentUrl.contains("ais-dev-cjko7cdnugq6kua6si75zu") || currentUrl == "https://example.com"
        if (isOldDefault) {
            viewModel.updateBaseUrl("")
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    try {
                        startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        Toast.makeText(this, "Please disable battery optimization manually in system settings", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Battery optimization is already disabled!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Operation not required on this Android version.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSettings()
    }
}
