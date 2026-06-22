package com.gateway.admin

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioAlertManager(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    enum class AlarmType {
        SOFT_CHIME,
        PHONE_RINGTONE,
        DIGITAL_BEEP
    }

    fun playSound(type: AlarmType, durationSeconds: Int, volumePercent: Int = 100) {
        stopSound()
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode

        var isDndActive = false
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = notificationManager.currentInterruptionFilter
                // All allows notifications. Priority/Alarms/None means DND status is active.
                if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    isDndActive = true
                }
            }
        } catch (e: Exception) {
            Log.e("AudioAlertManager", "Failed to get interruption filter: ${e.message}")
        }

        // Silent or Do Not Disturb: No sound, no vibration!
        if (ringerMode == AudioManager.RINGER_MODE_SILENT || isDndActive) {
            return
        }

        val canPlaySound = ringerMode == AudioManager.RINGER_MODE_NORMAL
        val canVibrate = ringerMode != AudioManager.RINGER_MODE_SILENT

        // Trigger Vibration if allowed
        if (canVibrate) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Vibrate with custom pattern: 500ms vibrate, 500ms sleep, repeating
                    val timings = longArrayOf(0, 500, 500)
                    val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationSeconds * 1000L)
                }
            } catch (e: Exception) {
                Log.e("AudioAlertManager", "Failed to vibrate: ${e.message}")
            }
        }

        // If vibration-only mode is active, do not play any alarm waves. Simply sleep and stop vibration.
        if (!canPlaySound) {
            playJob = scope.launch {
                delay(durationSeconds * 1000L)
                try {
                    vibrator.cancel()
                } catch (e: Exception) {
                    // Swallowed
                }
            }
            return
        }

        playJob = scope.launch {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Use 8x the min buffer size or at least 35280 bytes to ensure extra smooth queueing
            val bufferSize = (minBufferSize * 8).coerceAtLeast(35280)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM) // Bypasses silent/do-not-disturb, plays on Alarm channel!
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                Log.e("AudioAlertManager", "Failed to build AudioTrack with Builder: ${e.message}")
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_ALARM,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack = track
            
            try {
                track.play()
            } catch (e: Exception) {
                Log.e("AudioAlertManager", "Failed to start AudioTrack playback: ${e.message}")
                return@launch
            }

            val endTime = System.currentTimeMillis() + (durationSeconds * 1000L)
            var phase = 0.0
            val volFactor = (volumePercent.coerceIn(0, 100) / 100.0)

            while (System.currentTimeMillis() < endTime && track.state == AudioTrack.STATE_INITIALIZED) {
                val samplesCount = 4410 // 100ms batch
                val buffer = ShortArray(samplesCount)
                
                when (type) {
                    AlarmType.SOFT_CHIME -> {
                        // Soft elegant melodic piano and bell resonance chimes
                        val tMillis = System.currentTimeMillis() % 1200
                        val freq = if (tMillis < 250) 1200.0 else if (tMillis in 300..550) 1500.0 else 0.0
                        val decay = if (tMillis < 250) (250 - tMillis) / 250.0 else if (tMillis in 300..550) (550 - tMillis) / 250.0 else 0.0
                        
                        for (i in 0 until samplesCount) {
                            if (freq > 0) {
                                val s = sin(phase) * 32767.0 * 0.4 * decay * volFactor
                                buffer[i] = s.toInt().toShort()
                                phase = (phase + 2.0 * Math.PI * freq / sampleRate) % (2.0 * Math.PI)
                            } else {
                                buffer[i] = 0
                            }
                        }
                    }
                    AlarmType.PHONE_RINGTONE -> {
                        // Standard classic mechanical dual telephone ring tone
                        val tMillis = System.currentTimeMillis() % 2500
                        val shouldRing = tMillis < 350 || (tMillis in 500..850)
                        val freq1 = 440.0
                        
                        for (i in 0 until samplesCount) {
                            if (shouldRing) {
                                val wave = (sin(phase) + sin(phase * 1.09)) * 0.5
                                val s = wave * 32767.0 * 0.5 * volFactor
                                buffer[i] = s.toInt().toShort()
                                phase = (phase + 2.0 * Math.PI * freq1 / sampleRate) % (2.0 * Math.PI)
                            } else {
                                buffer[i] = 0
                            }
                        }
                    }
                    AlarmType.DIGITAL_BEEP -> {
                        // Quick alarm monitor digital pulse beep
                        val tMillis = System.currentTimeMillis() % 600
                        val shouldBeep = tMillis < 120 || (tMillis in 200..320)
                        val freq = 2000.0
                        
                        for (i in 0 until samplesCount) {
                            if (shouldBeep) {
                                val s = sin(phase) * 32767.0 * 0.5 * volFactor
                                buffer[i] = s.toInt().toShort()
                                phase = (phase + 2.0 * Math.PI * freq / sampleRate) % (2.0 * Math.PI)
                            } else {
                                buffer[i] = 0
                            }
                        }
                    }
                }
                
                track.write(buffer, 0, samplesCount)
                // Small delay to let AudioTrack's block self-regulate without starvation or tight loop CPU burning.
                delay(30)
            }

            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Swallowed
            }
            try {
                vibrator.cancel()
            } catch (e: Exception) {
                // Swallowed
            }
        }
    }

    fun stopSound() {
        playJob?.cancel()
        playJob = null
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            // Swallowed
        }
        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Swallowed
        }
        audioTrack = null
    }
}
