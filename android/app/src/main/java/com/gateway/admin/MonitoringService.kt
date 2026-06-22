package com.gateway.admin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var pollingJob: Job? = null
    private lateinit var audioAlertManager: AudioAlertManager
    private lateinit var sharedPreferences: SharedPreferences

    // Track seen checkouts (id + step) so we only alert once per step transition
    private val alertedCheckouts = mutableSetOf<String>()
    private val processedNewCheckouts = mutableSetOf<String>()

    companion object {
        const val CHANNEL_ID = "GatewayMonitorChannel"
        const val NOTIFICATION_ID = 991
        
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        audioAlertManager = AudioAlertManager(this)
        sharedPreferences = getSharedPreferences("gateway_monitor_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("pref_service_running", true).apply()
        
        // Acquire WakeLock to keep tracking active when CPU sleeps
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GatewayMonitor::PollingLock").apply {
            acquire()
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring system active. Polling server..."))
        
        startPolling()
    }

    private fun startPolling() {
        pollingJob = serviceScope.launch {
            while (isActive) {
                val baseUrl = sharedPreferences.getString("pref_base_url", "") ?: ""
                val pollIntervalSeconds = sharedPreferences.getInt("pref_poll_interval", 5).coerceIn(2, 60)
                val deviceId = sharedPreferences.getString("pref_device_id", "") ?: ""
                val brand = android.os.Build.MANUFACTURER ?: "Android"
                val model = android.os.Build.MODEL ?: "Device"
                val deviceName = "$brand $model"
                
                if (baseUrl.isNotBlank()) {
                    try {
                        val api = RetrofitClient.getService(baseUrl)
                        
                        // Dynamically check device licensing status first before pulling checkouts
                        if (deviceId.isNotBlank()) {
                            val checkResponse = api.checkDevice(CheckDeviceRequest(deviceId, deviceName))
                            if (checkResponse.success) {
                                sharedPreferences.edit().putString("pref_device_status", checkResponse.status).apply()
                                if (checkResponse.status != "approved") {
                                    val errStatusMsg = if (checkResponse.status == "blocked") {
                                        "আপনার মোবাইল ডিভাইসটি ব্লক করা হয়েছে!"
                                    } else {
                                        "অ্যাক্টিভেশন প্রয়োজন! লাইসেন্স কী দিন।"
                                    }
                                    sharedPreferences.edit()
                                        .putString("pref_last_sync_status", "Error: $errStatusMsg")
                                        .apply()
                                    updateNotification(errStatusMsg)
                                    stopSelf() // Stop this foreground service immediately
                                    break
                                }
                            }
                        }

                        val response = api.getActiveCheckouts()
                        
                        val timeStr = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date())
                        if (response.success && response.activeCheckouts != null) {
                            processCheckouts(response.activeCheckouts)
                            sharedPreferences.edit()
                                .putString("pref_last_sync_status", "Connected successfully. Active sessions: ${response.activeCheckouts.size}")
                                .putString("pref_last_sync_time", timeStr)
                                .apply()
                        } else {
                            sharedPreferences.edit()
                                .putString("pref_last_sync_status", "Server returned unsuccessful response")
                                .putString("pref_last_sync_time", timeStr)
                                .apply()
                        }
                    } catch (e: Exception) {
                        val rawMsg = e.localizedMessage ?: "Network error"
                        val userFriendlyMsg = if (rawMsg.contains("malformed JSON", ignoreCase = true) || 
                            rawMsg.contains("JsonReader", ignoreCase = true) || 
                            rawMsg.contains("setLenient", ignoreCase = true)) {
                            "Error: AI Studio link is login-protected (HTML returned). Use your LIVE public server URL."
                        } else {
                            "Error: $rawMsg"
                        }
                        
                        val timeStr = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date())
                        sharedPreferences.edit()
                            .putString("pref_last_sync_status", userFriendlyMsg)
                            .putString("pref_last_sync_time", timeStr)
                            .apply()
                        
                        // Polling error (offline, bad domain). Update notification to show status
                        updateNotification("Connecting: $userFriendlyMsg")
                    }
                } else {
                    val timeStr = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date())
                    sharedPreferences.edit()
                        .putString("pref_last_sync_status", "Idle: Please configure Server URL")
                        .putString("pref_last_sync_time", timeStr)
                        .apply()
                    updateNotification("Idle: Please configure a Server URL in Settings")
                }
                
                delay(pollIntervalSeconds * 1000L)
            }
        }
    }

    private suspend fun processCheckouts(checkouts: List<CheckoutItem>) {
        if (checkouts.isEmpty()) {
            updateNotification("No active customer sessions. Listening...")
            return
        }

        updateNotification("Active: Monitoring ${checkouts.size} checkout sessions")

        val gson = com.google.gson.Gson()
        val tongsJson = sharedPreferences.getString("pref_dynamic_tongs", "") ?: ""
        val tongs: List<TongConfig> = if (tongsJson.isNotBlank()) {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<TongConfig>>() {}.type
                gson.fromJson(tongsJson, listType)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        for (item in checkouts) {
            val step = item.step ?: 0 // Default to step 0 if not set

            // Detect brand-new customer sessions entering the gateway
            if (!processedNewCheckouts.contains(item.id)) {
                processedNewCheckouts.add(item.id)
                // If step 0 alarm is active, trigger it immediately
                val step0Tong = tongs.find { it.step == 0 }
                val isStep0Active = step0Tong?.isActive ?: true
                if (isStep0Active) {
                    val durationSeconds = step0Tong?.durationSeconds ?: sharedPreferences.getInt("pref_alarm_duration", 10).coerceIn(1, 60)
                    val alarmType = step0Tong?.type ?: AudioAlertManager.AlarmType.SOFT_CHIME
                    val volumePercent = step0Tong?.getSafeVolume() ?: 100
                    audioAlertManager.playSound(alarmType, durationSeconds, volumePercent)
                    triggerNotificationAlert(item, 0)

                    alertedCheckouts.add("${item.id}_step_0")

                    // If current step is also 0, we've fully alerted on it, so continue to the next item
                    if (step == 0) {
                        continue
                    }
                    // Otherwise stagger and wait 3 seconds so step 0 alarm is heard before playing step 1/2/3/etc.
                    delay(3000)
                }
            }

            // Create a unique key for checkout ID + step (so we can alert on each step once)
            val alertKey = "${item.id}_step_$step"

            if (!alertedCheckouts.contains(alertKey)) {
                alertedCheckouts.add(alertKey)
                
                val tong = tongs.find { it.step == step }
                val isTongActive = tong?.isActive ?: true
                val alarmType = tong?.type ?: AudioAlertManager.AlarmType.DIGITAL_BEEP
                
                if (isTongActive) {
                    val durationSeconds = tong?.durationSeconds ?: sharedPreferences.getInt("pref_alarm_duration", 10).coerceIn(1, 60)
                    val volumePercent = tong?.getSafeVolume() ?: 100
                    audioAlertManager.playSound(alarmType, durationSeconds, volumePercent)
                    triggerNotificationAlert(item, step)
                }
            }
        }
    }

    private fun getDefaultAlarmType(step: Int): String {
        return when (step) {
            0, 4 -> "SOFT_CHIME"
            1 -> "DIGITAL_BEEP"
            2 -> "PHONE_RINGTONE"
            else -> "DIGITAL_BEEP"
        }
    }

    private fun triggerNotificationAlert(item: CheckoutItem, step: Int) {
        val message = "Step $step: ${item.payerName ?: "Customer"} (৳${item.amount ?: 0.0})"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, item.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Checkout Action Alert 🔔")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(item.id.hashCode(), alertNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Gateway Monitoring Foreground",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gateway Monitor Running 🟢")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        isServiceRunning = false
        sharedPreferences.edit().putBoolean("pref_service_running", false).apply()
        pollingJob?.cancel()
        serviceScope.cancel()
        audioAlertManager.stopSound()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
