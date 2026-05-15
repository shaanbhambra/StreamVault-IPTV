package com.streamvault.app.debug

import android.content.Context
import android.util.Log

/**
 * Static entry point to start the debug API foreground service.
 * Called via reflection from MainActivity.
 */
object DebugApiStarter {
    private const val TAG = "DebugAPI"

    @JvmStatic
    fun start(context: Context) {
        try {
            DebugApiService.start(context.applicationContext)
            Log.i(TAG, "Debug API service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start debug API service", e)
        }
    }
}
