package com.streamvault.app.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.IBinder
import android.util.Log

/**
 * Foreground service that keeps the debug HTTP API alive even when the app is backgrounded.
 * Debug builds only.
 */
class DebugApiService : Service() {

    companion object {
        private const val TAG = "DebugAPI"
        private const val PORT = 8585
        private const val CHANNEL_ID = "debug_api"
        private const val NOTIFICATION_ID = 8585

        private var server: DebugApiServer? = null

        fun start(context: Context) {
            val intent = Intent(context, DebugApiService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DebugApiService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Start server on background thread to avoid StrictMode violations
        Thread {
            if (server == null) {
                try {
                    val dbPath = getDatabasePath("streamvault.db").absolutePath
                    val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
                    db.enableWriteAheadLogging()

                    server = DebugApiServer(applicationContext, db, PORT).also {
                        it.start()
                        Log.i(TAG, "══════════════════════════════════════")
                        Log.i(TAG, "  Debug API: http://localhost:$PORT")
                        Log.i(TAG, "  Foreground service — survives backgrounding")
                        Log.i(TAG, "══════════════════════════════════════")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start debug API server", e)
                }
            }
        }.start()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        Log.i(TAG, "Debug API server stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart if killed
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Debug API",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "StreamVault debug API server"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("StreamVault API")
            .setContentText("Debug API on port $PORT")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
