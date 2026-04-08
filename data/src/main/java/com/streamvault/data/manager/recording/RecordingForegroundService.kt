package com.streamvault.data.manager.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.streamvault.data.R
import com.streamvault.domain.manager.RecordingManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecordingForegroundService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecordingServiceEntryPoint {
        fun recordingManager(): RecordingManager
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            startForeground(NOTIFICATION_ID, buildNotification(activeCount = 0))
        }.onFailure { error ->
            Log.e("RecordingFgService", "Unable to enter foreground", error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ensureNotificationObserver()
        val manager = entryPoint().recordingManager()
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val recordingId = intent.getStringExtra(EXTRA_RECORDING_ID).orEmpty()
                serviceScope.launch { manager.promoteScheduledRecording(recordingId) }
            }
            ACTION_STOP_CAPTURE -> {
                val recordingId = intent.getStringExtra(EXTRA_RECORDING_ID).orEmpty()
                serviceScope.launch { manager.stopRecording(recordingId) }
            }
            ACTION_RECONCILE -> {
                serviceScope.launch { manager.reconcileRecordingState() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationObserver() {
        if (notificationJob != null) return
        notificationJob = serviceScope.launch {
            entryPoint().recordingManager().observeActiveRecordingCount().collectLatest { activeCount ->
                val count = activeCount.coerceAtLeast(0)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(count))
                if (count == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(activeCount: Int): Notification {
        val title = if (activeCount > 0) {
            "$activeCount recording${if (activeCount == 1) "" else "s"} in progress"
        } else {
            "Preparing recording service"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("StreamVault DVR")
            .setContentText(title)
            .setOngoing(activeCount > 0)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(defaultContentIntent())
            .build()
    }

    private fun defaultContentIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "StreamVault DVR",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active recording and scheduling status"
        }
        manager.createNotificationChannel(channel)
    }

    private fun entryPoint(): RecordingServiceEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, RecordingServiceEntryPoint::class.java)

    companion object {
        private const val CHANNEL_ID = "streamvault_recording"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START_CAPTURE = "com.streamvault.data.recording.service.START_CAPTURE"
        private const val ACTION_STOP_CAPTURE = "com.streamvault.data.recording.service.STOP_CAPTURE"
        private const val ACTION_RECONCILE = "com.streamvault.data.recording.service.RECONCILE"
        private const val EXTRA_RECORDING_ID = "recording_id"

        fun startCapture(context: Context, recordingId: String) {
            val intent = Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_START_CAPTURE)
                .putExtra(EXTRA_RECORDING_ID, recordingId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopRecording(context: Context, recordingId: String) {
            val intent = Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_STOP_CAPTURE)
                .putExtra(EXTRA_RECORDING_ID, recordingId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestReconcile(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_RECONCILE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIfIdle(context: Context) {
            context.stopService(Intent(context, RecordingForegroundService::class.java))
        }
    }
}
