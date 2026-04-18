package com.streamvault.data.manager.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager: AlarmManager? by lazy {
        context.getSystemService(AlarmManager::class.java)
    }

    fun scheduleStart(recordingId: String, whenMs: Long) {
        schedule(
            action = RecordingAlarmReceiver.ACTION_START_RECORDING,
            recordingId = recordingId,
            whenMs = whenMs
        )
    }

    fun scheduleStop(recordingId: String, whenMs: Long) {
        schedule(
            action = RecordingAlarmReceiver.ACTION_STOP_RECORDING,
            recordingId = recordingId,
            whenMs = whenMs
        )
    }

    fun cancel(recordingId: String) {
        alarmManager?.cancel(
            buildPendingIntent(
                action = RecordingAlarmReceiver.ACTION_START_RECORDING,
                recordingId = recordingId,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        alarmManager?.cancel(
            buildPendingIntent(
                action = RecordingAlarmReceiver.ACTION_STOP_RECORDING,
                recordingId = recordingId,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun schedule(action: String, recordingId: String, whenMs: Long) {
        val pendingIntent = buildPendingIntent(
            action = action,
            recordingId = recordingId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = alarmManager ?: return
        runCatching {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                whenMs,
                pendingIntent
            )
        }.getOrElse { ex ->
            android.util.Log.w("RecordingAlarmScheduler",
                "Exact alarm denied (SCHEDULE_EXACT_ALARM may be revoked), falling back to inexact alarm for $recordingId", ex)
            AlarmManagerCompat.setAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                whenMs,
                pendingIntent
            )
        }
    }

    private fun buildPendingIntent(action: String, recordingId: String, flags: Int): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(RecordingAlarmReceiver.EXTRA_RECORDING_ID, recordingId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(action, recordingId),
            intent,
            flags
        )
    }

    private fun requestCode(action: String, recordingId: String): Int {
        val uuid = runCatching { java.util.UUID.fromString(recordingId) }.getOrNull()
        val idHash = if (uuid != null) {
            (uuid.mostSignificantBits xor uuid.leastSignificantBits).toInt()
        } else {
            recordingId.hashCode()
        }
        return 31 * action.hashCode() + idHash
    }
}
