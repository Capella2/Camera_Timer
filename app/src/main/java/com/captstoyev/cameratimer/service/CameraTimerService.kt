package com.captstoyev.cameratimer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.captstoyev.cameratimer.MainActivity
import com.captstoyev.cameratimer.camera.CameraManager
import com.captstoyev.cameratimer.data.SettingsRepository
import com.captstoyev.cameratimer.data.UserSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CameraTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cameraManager: CameraManager
    private var timerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "CameraTimerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        cameraManager = CameraManager.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        if (timerJob != null) return

        val notification = createNotification("Camera Timer is running...")
        startForeground(NOTIFICATION_ID, notification)

        timerJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepository.settingsFlow.first()
                Log.d("CameraTimerService", "Taking photo with interval: ${settings.intervalSeconds}")
                cameraManager.takePhoto(
                    settings = settings,
                    onImageSaved = { uri -> Log.d("CameraTimerService", "Saved: $uri") },
                    onError = { e -> Log.e("CameraTimerService", "Error: ${e.message}") }
                )
                delay(settings.intervalSeconds * 1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Timer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Timer")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cameraManager.shutdown()
    }
}
