package com.streamvault.data.manager.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

class RecordingRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RecordingForegroundService.requestReconcile(context)
                RecordingReconcileWorker.enqueueOneShot(context)
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                if (userManager?.isUserUnlocked == true) {
                    RecordingForegroundService.requestReconcile(context)
                    RecordingReconcileWorker.enqueueOneShot(context)
                }
            }
        }
    }
}
