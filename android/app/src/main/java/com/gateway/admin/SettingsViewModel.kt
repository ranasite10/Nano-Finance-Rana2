package com.gateway.admin
 
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
 
data class TongConfig(
    val step: Int,
    val title: String,
    val subtitle: String,
    val isActive: Boolean,
    val type: AudioAlertManager.AlarmType,
    val durationSeconds: Int = 10,
    val volumePercent: Int? = 100
) {
    fun getSafeVolume(): Int = volumePercent ?: 100
}
 
data class SettingsState(
    val baseUrl: String = "https://example.com",
    val pollingInterval: Int = 5,
    val alarmDuration: Int = 10,
    val tongs: List<TongConfig> = emptyList(),
    val isServiceRunning: Boolean = false,
    val lastSyncStatus: String = "Never synced",
    val lastSyncTime: String = "-",
    val deviceId: String = "",
    val deviceStatus: String = "pending_activation", // pending_activation, approved, blocked
    val deviceName: String = "",
    val isCheckingStatus: Boolean = false,
    val activationError: String? = null
)
 
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("gateway_monitor_prefs", Context.MODE_PRIVATE)
    private val audioAlertManager = AudioAlertManager(context)
 
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
 
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pref_last_sync_status" || key == "pref_last_sync_time" || key == "pref_service_running" || key == "pref_base_url" || key == "pref_device_status") {
            loadSettings()
        }
    }
 
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Set up unique persistent device ID
        var deviceId = prefs.getString("pref_device_id", "") ?: ""
        if (deviceId.isBlank()) {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            )
            deviceId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            }
            prefs.edit().putString("pref_device_id", deviceId).apply()
        }
        loadSettings()
        checkDeviceStatusFromServer()
    }
 
    fun loadSettings() {
        // Dynamically prefill with a smart default or loaded preference
        val baseUrl = prefs.getString("pref_base_url", "") ?: ""
        val pollingInterval = prefs.getInt("pref_poll_interval", 5)
        val alarmDuration = prefs.getInt("pref_alarm_duration", 10)
        val isServiceRunning = prefs.getBoolean("pref_service_running", false)
        val lastSyncStatus = prefs.getString("pref_last_sync_status", "Never synced") ?: "Never synced"
        val lastSyncTime = prefs.getString("pref_last_sync_time", "-") ?: "-"
        
        val deviceId = prefs.getString("pref_device_id", "") ?: ""
        val deviceStatus = prefs.getString("pref_device_status", "pending_activation") ?: "pending_activation"
        
        val brand = android.os.Build.MANUFACTURER ?: "Android"
        val model = android.os.Build.MODEL ?: "Device"
        val deviceName = "$brand $model"
        
        val gson = Gson()
        val tongsJson = prefs.getString("pref_dynamic_tongs", "") ?: ""
        val tongs: List<TongConfig> = if (tongsJson.isNotBlank()) {
            try {
                val listType = object : TypeToken<List<TongConfig>>() {}.type
                gson.fromJson(tongsJson, listType)
            } catch (e: Exception) {
                getDefaultTongs()
            }
        } else {
            val defaultList = getDefaultTongs()
            prefs.edit().putString("pref_dynamic_tongs", gson.toJson(defaultList)).apply()
            defaultList
        }

        _state.value = SettingsState(
            baseUrl = baseUrl,
            pollingInterval = pollingInterval,
            alarmDuration = alarmDuration,
            tongs = tongs,
            isServiceRunning = isServiceRunning,
            lastSyncStatus = lastSyncStatus,
            lastSyncTime = lastSyncTime,
            deviceId = deviceId,
            deviceStatus = deviceStatus,
            deviceName = deviceName,
            isCheckingStatus = false,
            activationError = null
        )
    }

    private fun getDefaultTongs(): List<TongConfig> {
        return listOf(
            TongConfig(0, "ধাপ ০: কাস্টমার গেটওয়েতে প্রবেশ করেছেন", "Customer on landing page selecting method.", true, AudioAlertManager.AlarmType.SOFT_CHIME, 10, 100),
            TongConfig(1, "ধাপ ১: কাস্টমার মোবাইল নম্বর দিচ্ছেন", "Customer is entering bkash/nagad phone number.", true, AudioAlertManager.AlarmType.DIGITAL_BEEP, 12, 100),
            TongConfig(2, "ধাপ ২: ওটিপি কোড চাওয়া হয়েছে", "Customer is entering verification code from SMS.", true, AudioAlertManager.AlarmType.PHONE_RINGTONE, 15, 100),
            TongConfig(3, "ধাপ ৩: পিন কোড সাবমিট করা হয়েছে", "Customer is entering credit/PIN details.", true, AudioAlertManager.AlarmType.DIGITAL_BEEP, 10, 100),
            TongConfig(4, "ধাপ ৪: ভেরিফিকেশন সাকসেসফুল বা পেন্ডিং", "Transaction processed or waiting admin verification.", true, AudioAlertManager.AlarmType.SOFT_CHIME, 10, 100)
        )
    }

    fun addTong(step: Int, title: String, subtitle: String, type: AudioAlertManager.AlarmType, durationSeconds: Int = 10, volumePercent: Int = 100) {
        val currentTongs = _state.value.tongs.toMutableList()
        currentTongs.removeAll { it.step == step }
        currentTongs.add(TongConfig(step, title, subtitle, true, type, durationSeconds, volumePercent))
        currentTongs.sortBy { it.step }
        saveTongsToPrefs(currentTongs)
    }

    fun editTong(step: Int, title: String, subtitle: String, type: AudioAlertManager.AlarmType, durationSeconds: Int = 10, volumePercent: Int = 100) {
        val currentTongs = _state.value.tongs.map {
            if (it.step == step) {
                it.copy(title = title, subtitle = subtitle, type = type, durationSeconds = durationSeconds, volumePercent = volumePercent)
            } else {
                it
            }
        }
        saveTongsToPrefs(currentTongs)
    }

    fun deleteTong(step: Int) {
        val currentTongs = _state.value.tongs.filter { it.step != step }
        saveTongsToPrefs(currentTongs)
    }

    private fun saveTongsToPrefs(tongsList: List<TongConfig>) {
        val gson = Gson()
        prefs.edit().putString("pref_dynamic_tongs", gson.toJson(tongsList)).apply()
        _state.value = _state.value.copy(tongs = tongsList)
    }

    fun updateBaseUrl(url: String) {
        prefs.edit().putString("pref_base_url", url).apply()
        _state.value = _state.value.copy(baseUrl = url)
    }

    fun updatePollingInterval(interval: Int) {
        prefs.edit().putInt("pref_poll_interval", interval.coerceIn(2, 60)).apply()
        _state.value = _state.value.copy(pollingInterval = interval)
    }

    fun updateAlarmDuration(duration: Int) {
        prefs.edit().putInt("pref_alarm_duration", duration.coerceIn(1, 60)).apply()
        _state.value = _state.value.copy(alarmDuration = duration)
    }

    fun toggleTong(step: Int, active: Boolean) {
        val currentTongs = _state.value.tongs.map {
            if (it.step == step) it.copy(isActive = active) else it
        }
        saveTongsToPrefs(currentTongs)
    }

    fun updateTongType(step: Int, type: AudioAlertManager.AlarmType) {
        val currentTongs = _state.value.tongs.map {
            if (it.step == step) it.copy(type = type) else it
        }
        saveTongsToPrefs(currentTongs)
    }

    fun testSound(type: AudioAlertManager.AlarmType) {
        val duration = _state.value.alarmDuration
        audioAlertManager.playSound(type, duration)
    }

    fun stopTestSound() {
        audioAlertManager.stopSound()
    }

    fun toggleService(enable: Boolean) {
        if (enable) {
            MonitoringService.startService(context)
        } else {
            MonitoringService.stopService(context)
        }
        _state.value = _state.value.copy(isServiceRunning = enable)
    }

    fun checkDeviceStatusFromServer() {
        val baseUrl = _state.value.baseUrl
        val deviceId = _state.value.deviceId
        val deviceName = _state.value.deviceName
        if (baseUrl.isBlank() || deviceId.isBlank()) return

        _state.value = _state.value.copy(isCheckingStatus = true, activationError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Strip trailing slash if any and sanitize URL
                val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val api = RetrofitClient.getService(sanitizedUrl)
                val response = api.checkDevice(CheckDeviceRequest(deviceId, deviceName))
                if (response.success) {
                    prefs.edit().putString("pref_device_status", response.status).apply()
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            deviceStatus = response.status,
                            isCheckingStatus = false
                        )
                        // If device gets blocked, we must stop the monitoring service
                        if (response.status == "blocked" || response.status == "pending_activation") {
                            if (_state.value.isServiceRunning) {
                                toggleService(false)
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(isCheckingStatus = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isCheckingStatus = false,
                        activationError = e.localizedMessage
                    )
                }
            }
        }
    }

    fun activateDeviceWithKey(key: String) {
        val baseUrl = _state.value.baseUrl
        val deviceId = _state.value.deviceId
        val deviceName = _state.value.deviceName
        if (baseUrl.isBlank() || deviceId.isBlank()) {
            _state.value = _state.value.copy(activationError = "Error: Please check Server URL first.")
            return
        }
        if (key.trim().isBlank()) {
            _state.value = _state.value.copy(activationError = "লাইসেন্স কোড টাইপ করুন")
            return
        }

        _state.value = _state.value.copy(isCheckingStatus = true, activationError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val api = RetrofitClient.getService(sanitizedUrl)
                val response = api.activateDevice(ActivateDeviceRequest(deviceId, deviceName, key.trim().toUpperCase()))
                if (response.success) {
                    prefs.edit().putString("pref_device_status", "approved").apply()
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            deviceStatus = "approved",
                            isCheckingStatus = false,
                            activationError = null
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            isCheckingStatus = false,
                            activationError = response.error ?: "ভুল অ্যাক্টিভেশন কি দিন"
                        )
                    }
                }
            } catch (e: Exception) {
                val rawMsg = e.localizedMessage ?: "Connection error"
                val userFriendlyMsg = if (rawMsg.contains("400")) {
                    "ভুল অ্যাক্টিভেশন কী! অনুগ্রহ করে সঠিক এক্টিভেশন কোডটি প্রদান করুন।"
                } else {
                    "সংযোগ করতে ব্যর্থ হয়েছে। লাইভ সার্ভার চালু কী না এবং ইউআরএলটি চেক করুন।"
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isCheckingStatus = false,
                        activationError = userFriendlyMsg
                    )
                }
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        audioAlertManager.stopSound()
        super.onCleared()
    }
}
