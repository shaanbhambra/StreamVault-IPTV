package com.streamvault.data.sync

import android.util.Log
import com.streamvault.data.local.StreamVaultDatabase
import com.streamvault.data.local.dao.EpgProgrammeDao
import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.data.local.dao.FavoriteDao
import com.streamvault.data.local.dao.ProgramDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DatabaseMaintenanceManager @Inject constructor(
    private val database: StreamVaultDatabase,
    private val programDao: ProgramDao,
    private val epgProgrammeDao: EpgProgrammeDao,
    private val episodeDao: EpisodeDao,
    private val favoriteDao: FavoriteDao,
    private val syncManager: SyncManager
) {

    suspend fun runDailyMaintenance(now: Long = System.currentTimeMillis()): MaintenanceReport = withContext(Dispatchers.IO) {
        val oldProgramThreshold = now - PROGRAM_RETENTION_MILLIS
        val deletedPrograms = programDao.deleteOld(oldProgramThreshold)
        val deletedExternalProgrammes = epgProgrammeDao.deleteOld(oldProgramThreshold)
        val deletedOrphanEpisodes = episodeDao.deleteOrphans()
        val deletedStaleFavorites = favoriteDao.deleteMissingLiveFavorites() +
            favoriteDao.deleteMissingMovieFavorites() +
            favoriteDao.deleteMissingSeriesFavorites()

        val statsBeforeVacuum = collectStorageStats()
        val vacuumRan = if (shouldVacuum(statsBeforeVacuum) && !isSyncActive()) {
            runVacuum()
        } else {
            if (shouldVacuum(statsBeforeVacuum)) {
                Log.i(TAG, "VACUUM skipped: a provider sync is currently active")
            }
            false
        }
        val statsAfterVacuum = collectStorageStats()

        MaintenanceReport(
            deletedPrograms = deletedPrograms,
            deletedExternalProgrammes = deletedExternalProgrammes,
            deletedOrphanEpisodes = deletedOrphanEpisodes,
            deletedStaleFavorites = deletedStaleFavorites,
            vacuumRan = vacuumRan,
            statsBeforeVacuum = statsBeforeVacuum,
            statsAfterVacuum = statsAfterVacuum
        )
    }

    private fun collectStorageStats(): DatabaseStorageStats {
        val sqliteDb = database.openHelper.writableDatabase
        val pageSize = sqliteDb.longPragma("page_size")
        val pageCount = sqliteDb.longPragma("page_count")
        val freelistCount = sqliteDb.longPragma("freelist_count")
        val databasePath = sqliteDb.path ?: return DatabaseStorageStats(
            pageSizeBytes = pageSize,
            pageCount = pageCount,
            freelistCount = freelistCount,
            mainDbBytes = 0L,
            walBytes = 0L
        )
        val databaseFile = File(databasePath)
        val walFile = File("$databasePath-wal")
        return DatabaseStorageStats(
            pageSizeBytes = pageSize,
            pageCount = pageCount,
            freelistCount = freelistCount,
            mainDbBytes = databaseFile.length(),
            walBytes = walFile.takeIf(File::exists)?.length() ?: 0L
        )
    }

    /** Returns true if VACUUM ran, false if skipped due to busy readers. */
    private suspend fun runVacuum(): Boolean = withContext(Dispatchers.IO) {
        val sqliteDb = database.openHelper.writableDatabase

        // Check WAL checkpoint result: column 1 = pages not yet checkpointed (busy readers).
        // If any pages are busy, active read transactions exist — VACUUM would block them all.
        val busyPages = sqliteDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(1) else 0
        }

        if (busyPages > 0) {
            Log.w(TAG, "wal_checkpoint(TRUNCATE) had $busyPages busy pages; VACUUM skipped this cycle")
            return@withContext false
        }

        val durationMs = kotlin.system.measureTimeMillis {
            sqliteDb.execSQL("VACUUM")
        }
        Log.i(TAG, "Database VACUUM completed in ${durationMs}ms")
        true
    }

    /** Returns true if any provider sync mutex is currently held. */
    private fun isSyncActive(): Boolean = syncManager.isAnySyncActive()

    private fun shouldVacuum(stats: DatabaseStorageStats): Boolean {
        if (stats.reclaimableBytes < MIN_RECLAIMABLE_BYTES) return false
        if (stats.mainDbBytes < MIN_DATABASE_BYTES) return false
        return stats.reclaimableBytes * 100 >= stats.mainDbBytes * RECLAIMABLE_PERCENT_THRESHOLD
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.longPragma(pragma: String): Long {
        query("PRAGMA $pragma").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    data class MaintenanceReport(
        val deletedPrograms: Int,
        val deletedExternalProgrammes: Int = 0,
        val deletedOrphanEpisodes: Int,
        val deletedStaleFavorites: Int,
        val vacuumRan: Boolean,
        val statsBeforeVacuum: DatabaseStorageStats,
        val statsAfterVacuum: DatabaseStorageStats
    )

    data class DatabaseStorageStats(
        val pageSizeBytes: Long,
        val pageCount: Long,
        val freelistCount: Long,
        val mainDbBytes: Long,
        val walBytes: Long
    ) {
        val reclaimableBytes: Long get() = pageSizeBytes * freelistCount
        val logicalDbBytes: Long get() = pageSizeBytes * pageCount
    }

    companion object {
        private const val TAG = "DbMaintenance"
        private const val PROGRAM_RETENTION_MILLIS = 24L * 60 * 60 * 1000L
        private const val MIN_RECLAIMABLE_BYTES = 32L * 1024 * 1024
        private const val MIN_DATABASE_BYTES = 128L * 1024 * 1024
        private const val RECLAIMABLE_PERCENT_THRESHOLD = 20L
    }
}