package com.streamvault.data.manager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.data.local.dao.RecordingRunDao
import com.streamvault.data.local.dao.RecordingScheduleDao
import com.streamvault.data.local.dao.RecordingStorageDao
import com.streamvault.data.local.entity.RecordingRunEntity
import com.streamvault.data.local.entity.RecordingScheduleEntity
import com.streamvault.data.local.entity.RecordingStorageEntity
import com.streamvault.data.manager.recording.CaptureProgress
import com.streamvault.data.manager.recording.HlsLiveCaptureEngine
import com.streamvault.data.manager.recording.RecordingAlarmScheduler
import com.streamvault.data.manager.recording.RecordingForegroundService
import com.streamvault.data.manager.recording.RecordingOutputTarget
import com.streamvault.data.manager.recording.RecordingSourceResolver
import com.streamvault.data.manager.recording.ResolvedRecordingSource
import com.streamvault.data.manager.recording.TsPassThroughCaptureEngine
import com.streamvault.data.manager.recording.UnsupportedRecordingException
import com.streamvault.data.manager.recording.asPersistenceValues
import com.streamvault.data.manager.recording.createOutputTarget
import com.streamvault.data.manager.recording.deleteOutputTarget
import com.streamvault.data.manager.recording.headersFromJson
import com.streamvault.data.manager.recording.headersToJson
import com.streamvault.data.manager.recording.inferFailureCategory
import com.streamvault.data.manager.recording.resolveStorageDetails
import com.streamvault.data.manager.recording.sanitizeRecordingFileName
import com.streamvault.data.manager.recording.toEntity
import com.streamvault.data.manager.recording.toDomain
import com.streamvault.domain.manager.RecordingManager
import com.streamvault.domain.model.RecordingFailureCategory
import com.streamvault.domain.model.RecordingItem
import com.streamvault.domain.model.RecordingRecurrence
import com.streamvault.domain.model.RecordingRequest
import com.streamvault.domain.model.RecordingSourceType
import com.streamvault.domain.model.RecordingStatus
import com.streamvault.domain.model.RecordingStorageConfig
import com.streamvault.domain.model.RecordingStorageState
import com.streamvault.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "RecordingManager"

@Singleton
class RecordingManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val providerDao: ProviderDao,
    private val recordingScheduleDao: RecordingScheduleDao,
    private val recordingRunDao: RecordingRunDao,
    private val recordingStorageDao: RecordingStorageDao,
    private val recordingSourceResolver: RecordingSourceResolver,
    private val tsPassThroughCaptureEngine: TsPassThroughCaptureEngine,
    private val hlsLiveCaptureEngine: HlsLiveCaptureEngine,
    private val alarmScheduler: RecordingAlarmScheduler
) : RecordingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeJobsMutex = Mutex()
    private val legacyStateFile by lazy { File(File(context.filesDir, "recordings"), "recordings_state.json") }

    init {
        scope.launch {
            migrateLegacyStateIfNeeded()
            ensureStorageState()
            reconcileRecordingState()
        }
    }

    override fun observeRecordingItems(): Flow<List<RecordingItem>> =
        recordingRunDao.observeAll().map { runs -> runs.map { it.toDomain() } }

    override fun observeStorageState(): Flow<RecordingStorageState> =
        recordingStorageDao.observe().map { entity ->
            (entity ?: ensureStorageStateSync()).toDomain()
        }

    override suspend fun startManualRecording(request: RecordingRequest): Result<RecordingItem> = withContext(Dispatchers.IO) {
        runCatching {
            val storage = ensureStorageStateSync()
            if (!storage.isWritable) {
                return@withContext Result.error("Recording storage is not writable.")
            }
            validateRecordingWindow(request.scheduledStartMs, request.scheduledEndMs, request.providerId)
                ?.let { return@withContext Result.error(it) }

            val scheduleId = recordingScheduleDao.insert(
                RecordingScheduleEntity(
                    providerId = request.providerId,
                    channelId = request.channelId,
                    channelName = request.channelName,
                    streamUrl = request.streamUrl,
                    programTitle = request.programTitle,
                    requestedStartMs = request.scheduledStartMs,
                    requestedEndMs = request.scheduledEndMs,
                    recurrence = RecordingRecurrence.NONE,
                    enabled = true,
                    isManual = true,
                    priority = request.priority
                )
            )

            val recordingId = UUID.randomUUID().toString()
            val source = resolveRecordableSource(request.providerId, request.channelId, request.streamUrl)
            val outputTarget = createOutputTarget(
                context = context,
                storage = storage,
                fileName = sanitizeRecordingFileName(
                    channelName = request.channelName,
                    programTitle = request.programTitle,
                    startMs = request.scheduledStartMs,
                    pattern = storage.fileNamePattern
                )
            )
            val (outputUri, outputDisplayPath) = outputTarget.asPersistenceValues()
            val now = System.currentTimeMillis()
            val run = RecordingRunEntity(
                id = recordingId,
                scheduleId = scheduleId,
                providerId = request.providerId,
                channelId = request.channelId,
                channelName = request.channelName,
                streamUrl = request.streamUrl,
                programTitle = request.programTitle,
                scheduledStartMs = request.scheduledStartMs,
                scheduledEndMs = request.scheduledEndMs,
                recurrence = RecordingRecurrence.NONE,
                status = RecordingStatus.RECORDING,
                sourceType = source.sourceType,
                resolvedUrl = source.url,
                headersJson = headersToJson(gson, source.headers),
                userAgent = source.userAgent,
                expirationTime = source.expirationTime,
                providerLabel = source.providerLabel,
                outputUri = outputUri,
                outputDisplayPath = outputDisplayPath,
                startedAtMs = now,
                scheduleEnabled = true,
                priority = request.priority,
                alarmStopAtMs = request.scheduledEndMs,
                createdAt = now,
                updatedAt = now
            )
            recordingRunDao.insert(run)
            alarmScheduler.scheduleStop(recordingId, request.scheduledEndMs)
            when (val startResult = startCapture(run)) {
                is Result.Success -> Unit
                is Result.Error -> throw IllegalStateException(startResult.message, startResult.exception)
                Result.Loading -> Unit
            }
            run.toStandaloneDomain()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error -> Result.error(error.message ?: "Failed to start recording", error) }
        )
    }

    override suspend fun scheduleRecording(request: RecordingRequest): Result<RecordingItem> = withContext(Dispatchers.IO) {
        runCatching {
            val storage = ensureStorageStateSync()
            if (!storage.isWritable) {
                return@withContext Result.error("Recording storage is not writable.")
            }
            validateRecordingWindow(request.scheduledStartMs, request.scheduledEndMs, request.providerId)
                ?.let { return@withContext Result.error(it) }

            val recurringRuleId = request.recurringRuleId
                ?: request.recurrence.takeIf { it != RecordingRecurrence.NONE }?.let { UUID.randomUUID().toString() }
            val scheduleId = recordingScheduleDao.insert(
                RecordingScheduleEntity(
                    providerId = request.providerId,
                    channelId = request.channelId,
                    channelName = request.channelName,
                    streamUrl = request.streamUrl,
                    programTitle = request.programTitle,
                    requestedStartMs = request.scheduledStartMs,
                    requestedEndMs = request.scheduledEndMs,
                    recurrence = request.recurrence,
                    recurringRuleId = recurringRuleId,
                    enabled = true,
                    isManual = false,
                    priority = request.priority
                )
            )
            val run = createPendingRun(
                scheduleId = scheduleId,
                request = request.copy(recurringRuleId = recurringRuleId)
            )
            recordingRunDao.insert(run)
            alarmScheduler.scheduleStart(run.id, run.scheduledStartMs)
            run.toStandaloneDomain()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error -> Result.error(error.message ?: "Failed to schedule recording", error) }
        )
    }

    override suspend fun stopRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        cancelActiveJob(recordingId)
        alarmScheduler.cancel(recordingId)
        val now = System.currentTimeMillis()
        recordingRunDao.update(
            run.copy(
                status = if (run.bytesWritten > 0L) RecordingStatus.COMPLETED else RecordingStatus.CANCELLED,
                endedAtMs = now,
                terminalAtMs = now,
                updatedAt = now
            )
        )
        Result.success(Unit)
    }

    override suspend fun cancelRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        cancelActiveJob(recordingId)
        alarmScheduler.cancel(recordingId)
        val now = System.currentTimeMillis()
        recordingRunDao.update(
            run.copy(
                status = RecordingStatus.CANCELLED,
                terminalAtMs = now,
                endedAtMs = now,
                scheduleEnabled = false,
                updatedAt = now
            )
        )
        recordingScheduleDao.getById(run.scheduleId)?.let { schedule ->
            recordingScheduleDao.update(schedule.copy(enabled = false, updatedAt = now))
        }
        Result.success(Unit)
    }

    override suspend fun deleteRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        if (run.status == RecordingStatus.SCHEDULED || run.status == RecordingStatus.RECORDING) {
            return@withContext Result.error("Only finished recordings can be deleted.")
        }
        deleteOutputTarget(context, run.outputUri, run.outputDisplayPath)
        recordingRunDao.delete(recordingId)
        Result.success(Unit)
    }

    override suspend fun retryRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        val schedule = recordingScheduleDao.getById(run.scheduleId) ?: return@withContext Result.error("Recording schedule not found")
        val retriedRun = createPendingRun(
            scheduleId = schedule.id,
            request = RecordingRequest(
                providerId = schedule.providerId,
                channelId = schedule.channelId,
                channelName = schedule.channelName,
                streamUrl = schedule.streamUrl,
                scheduledStartMs = maxOf(System.currentTimeMillis() + 2_000L, schedule.requestedStartMs),
                scheduledEndMs = schedule.requestedEndMs,
                programTitle = schedule.programTitle,
                recurrence = schedule.recurrence,
                recurringRuleId = schedule.recurringRuleId,
                priority = schedule.priority
            )
        )
        recordingRunDao.insert(retriedRun)
        alarmScheduler.scheduleStart(retriedRun.id, retriedRun.scheduledStartMs)
        Result.success(Unit)
    }

    override suspend fun setScheduleEnabled(recordingId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        val schedule = recordingScheduleDao.getById(run.scheduleId) ?: return@withContext Result.error("Recording schedule not found")
        val now = System.currentTimeMillis()
        recordingScheduleDao.update(schedule.copy(enabled = enabled, updatedAt = now))
        recordingRunDao.update(run.copy(scheduleEnabled = enabled, updatedAt = now))
        if (enabled && run.status == RecordingStatus.SCHEDULED) {
            alarmScheduler.scheduleStart(run.id, run.scheduledStartMs)
        } else {
            alarmScheduler.cancel(run.id)
        }
        Result.success(Unit)
    }

    override suspend fun updateStorageConfig(config: RecordingStorageConfig): Result<RecordingStorageState> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = recordingStorageDao.get()
            val (outputDirectory, availableBytes, isWritable) = resolveStorageDetails(context, config.treeUri)
            val entity = config.toEntity(existing, outputDirectory, availableBytes, isWritable)
            recordingStorageDao.upsert(entity)
            reconcileRecordingState()
            entity.toDomain()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error -> Result.error(error.message ?: "Failed to update recording storage", error) }
        )
    }

    override suspend fun reconcileRecordingState(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching<Unit> {
            ensureStorageState()
            recordingRunDao.getAlarmManagedScheduledRuns()
                .filter { it.scheduleEnabled && it.status == RecordingStatus.SCHEDULED }
                .forEach { run ->
                    if (run.scheduledEndMs <= System.currentTimeMillis()) {
                        markRunFailed(run.id, "Recording window expired before capture started.", RecordingFailureCategory.UNKNOWN)
                    } else {
                        alarmScheduler.scheduleStart(run.id, run.scheduledStartMs)
                    }
                }
            recordingRunDao.getRecordingRuns().forEach { run ->
                if (run.scheduledEndMs <= System.currentTimeMillis()) {
                    stopRecording(run.id)
                } else if (!isActiveJob(run.id)) {
                    RecordingForegroundService.startCapture(context, run.id)
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error -> Result.error(error.message ?: "Failed to reconcile recording state", error) }
        )
    }

    override suspend fun promoteScheduledRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val run = recordingRunDao.getById(recordingId) ?: return@withContext Result.error("Recording not found")
        when (run.status) {
            RecordingStatus.RECORDING -> startCapture(run)
            RecordingStatus.SCHEDULED -> {
                val source = resolveRecordableSource(run.providerId, run.channelId, run.streamUrl)
                val storage = ensureStorageStateSync()
                if (!storage.isWritable) {
                    return@withContext Result.error("Recording storage is not writable.")
                }
                val outputTarget = if (!run.outputUri.isNullOrBlank() || !run.outputDisplayPath.isNullOrBlank()) {
                    existingOutputTarget(run)
                } else {
                    createOutputTarget(
                        context = context,
                        storage = storage,
                        fileName = sanitizeRecordingFileName(run.channelName, run.programTitle, run.scheduledStartMs, storage.fileNamePattern)
                    )
                }
                val (outputUri, outputDisplayPath) = outputTarget.asPersistenceValues()
                val now = System.currentTimeMillis()
                val updatedRun = run.copy(
                    status = RecordingStatus.RECORDING,
                    sourceType = source.sourceType,
                    resolvedUrl = source.url,
                    headersJson = headersToJson(gson, source.headers),
                    userAgent = source.userAgent,
                    expirationTime = source.expirationTime,
                    providerLabel = source.providerLabel,
                    outputUri = outputUri ?: run.outputUri,
                    outputDisplayPath = outputDisplayPath ?: run.outputDisplayPath,
                    startedAtMs = now,
                    updatedAt = now,
                    alarmStopAtMs = run.scheduledEndMs
                )
                recordingRunDao.update(updatedRun)
                alarmScheduler.scheduleStop(updatedRun.id, updatedRun.scheduledEndMs)
                spawnNextRecurringRunIfNeeded(updatedRun)
                startCapture(updatedRun)
            }
            else -> Result.error("Recording is no longer active.")
        }
    }

    internal suspend fun onCaptureFinished(recordingId: String) {
        val remaining = observeActiveRecordingCountSync().coerceAtLeast(0)
        if (remaining == 0) {
            RecordingForegroundService.stopIfIdle(context)
        }
    }

    private suspend fun startCapture(run: RecordingRunEntity): Result<Unit> {
        if (isActiveJob(run.id)) return Result.success(Unit)
        val target = existingOutputTarget(run)
        val source = ResolvedRecordingSource(
            url = run.resolvedUrl ?: return Result.error("Recording stream URL could not be resolved."),
            sourceType = run.sourceType,
            headers = headersFromJson(gson, run.headersJson),
            userAgent = run.userAgent,
            expirationTime = run.expirationTime,
            providerLabel = run.providerLabel
        )
        val engine = when (source.sourceType) {
            RecordingSourceType.HLS -> hlsLiveCaptureEngine
            RecordingSourceType.TS,
            RecordingSourceType.UNKNOWN -> tsPassThroughCaptureEngine
            RecordingSourceType.DASH -> return Result.error("DASH live recording is not supported yet.")
        }
        val job = scope.launch {
            try {
                engine.capture(
                    source = source,
                    outputTarget = target,
                    contentResolver = context.contentResolver,
                    scheduledEndMs = run.scheduledEndMs,
                    onProgress = { progress -> updateRunProgress(run.id, progress) }
                )
                completeRun(run.id)
            } catch (cancelled: CancellationException) {
                Log.i("RecordingManager", "Capture cancelled for ${run.id}")
            } catch (unsupported: UnsupportedRecordingException) {
                markRunFailed(run.id, unsupported.message ?: "Recording format is unsupported.", unsupported.category)
            } catch (error: Throwable) {
                markRunFailed(run.id, error.message ?: "Recording failed.", inferFailureCategory(error.message))
            } finally {
                removeActiveJob(run.id)
                onCaptureFinished(run.id)
            }
        }
        registerActiveJob(run.id, job)
        return Result.success(Unit)
    }

    private suspend fun migrateLegacyStateIfNeeded() {
        if (!legacyStateFile.exists()) return
        val hasExistingRuns = runCatching {
            recordingRunDao.getByStatus(RecordingStatus.SCHEDULED).isNotEmpty() || recordingRunDao.getRecordingRuns().isNotEmpty()
        }.getOrDefault(false)
        if (hasExistingRuns) return
        val listType = object : TypeToken<List<RecordingItem>>() {}.type
        val legacyItems = runCatching {
            FileInputStream(legacyStateFile).bufferedReader().use { reader ->
                gson.fromJson<List<RecordingItem>>(reader, listType).orEmpty()
            }
        }.getOrDefault(emptyList())
        legacyItems.forEach { item ->
            val scheduleId = recordingScheduleDao.insert(
                RecordingScheduleEntity(
                    providerId = item.providerId,
                    channelId = item.channelId,
                    channelName = item.channelName,
                    streamUrl = item.streamUrl,
                    programTitle = item.programTitle,
                    requestedStartMs = item.scheduledStartMs,
                    requestedEndMs = item.scheduledEndMs,
                    recurrence = item.recurrence,
                    recurringRuleId = item.recurringRuleId,
                    enabled = item.scheduleEnabled,
                    isManual = item.recurrence == RecordingRecurrence.NONE && item.status == RecordingStatus.RECORDING,
                    priority = item.priority
                )
            )
            recordingRunDao.insert(
                RecordingRunEntity(
                    id = item.id,
                    scheduleId = scheduleId,
                    providerId = item.providerId,
                    channelId = item.channelId,
                    channelName = item.channelName,
                    streamUrl = item.streamUrl,
                    programTitle = item.programTitle,
                    scheduledStartMs = item.scheduledStartMs,
                    scheduledEndMs = item.scheduledEndMs,
                    recurrence = item.recurrence,
                    recurringRuleId = item.recurringRuleId,
                    status = item.status,
                    sourceType = item.sourceType,
                    outputUri = item.outputUri,
                    outputDisplayPath = item.outputDisplayPath ?: item.outputPath,
                    bytesWritten = item.bytesWritten,
                    averageThroughputBytesPerSecond = item.averageThroughputBytesPerSecond,
                    retryCount = item.retryCount,
                    lastProgressAtMs = item.lastProgressAtMs,
                    failureCategory = item.failureCategory,
                    failureReason = item.failureReason,
                    terminalAtMs = item.terminalAtMs,
                    startedAtMs = item.scheduledStartMs,
                    endedAtMs = item.terminalAtMs,
                    scheduleEnabled = item.scheduleEnabled,
                    priority = item.priority
                )
            )
        }
        legacyStateFile.delete()
    }

    private suspend fun ensureStorageState() {
        ensureStorageStateSync()
    }

    private suspend fun ensureStorageStateSync(): RecordingStorageEntity {
        val existing = recordingStorageDao.get()
        if (existing != null) {
            val (outputDirectory, availableBytes, isWritable) = resolveStorageDetails(context, existing.treeUri)
            val refreshed = existing.copy(
                outputDirectory = outputDirectory,
                availableBytes = availableBytes,
                isWritable = isWritable,
                updatedAt = System.currentTimeMillis()
            )
            recordingStorageDao.upsert(refreshed)
            return refreshed
        }
        val (outputDirectory, availableBytes, isWritable) = resolveStorageDetails(context, null)
        return RecordingStorageEntity(
            outputDirectory = outputDirectory,
            availableBytes = availableBytes,
            isWritable = isWritable
        ).also { recordingStorageDao.upsert(it) }
    }

    private suspend fun resolveRecordableSource(providerId: Long, channelId: Long, streamUrl: String): ResolvedRecordingSource {
        val source = recordingSourceResolver.resolveLiveSource(providerId, channelId, streamUrl)
        return when (source.sourceType) {
            RecordingSourceType.DASH -> throw UnsupportedRecordingException("DASH live recording is not supported yet.", RecordingFailureCategory.FORMAT_UNSUPPORTED)
            else -> source
        }
    }

    private suspend fun updateRunProgress(recordingId: String, progress: CaptureProgress) {
        val run = recordingRunDao.getById(recordingId) ?: return
        recordingRunDao.update(
            run.copy(
                bytesWritten = progress.bytesWritten,
                averageThroughputBytesPerSecond = progress.averageThroughputBytesPerSecond,
                retryCount = maxOf(run.retryCount, progress.retryCount),
                lastProgressAtMs = progress.lastProgressAtMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun completeRun(recordingId: String) {
        val run = recordingRunDao.getById(recordingId) ?: return
        val now = System.currentTimeMillis()
        recordingRunDao.update(
            run.copy(
                status = RecordingStatus.COMPLETED,
                terminalAtMs = now,
                endedAtMs = now,
                updatedAt = now
            )
        )
        alarmScheduler.cancel(recordingId)
    }

    private suspend fun markRunFailed(recordingId: String, reason: String, category: RecordingFailureCategory) {
        val run = recordingRunDao.getById(recordingId) ?: return
        val now = System.currentTimeMillis()
        recordingRunDao.update(
            run.copy(
                status = RecordingStatus.FAILED,
                failureReason = reason,
                failureCategory = category,
                terminalAtMs = now,
                endedAtMs = now,
                updatedAt = now
            )
        )
        alarmScheduler.cancel(recordingId)
    }

    private fun createPendingRun(scheduleId: Long, request: RecordingRequest): RecordingRunEntity {
        val now = System.currentTimeMillis()
        return RecordingRunEntity(
            id = UUID.randomUUID().toString(),
            scheduleId = scheduleId,
            providerId = request.providerId,
            channelId = request.channelId,
            channelName = request.channelName,
            streamUrl = request.streamUrl,
            programTitle = request.programTitle,
            scheduledStartMs = request.scheduledStartMs,
            scheduledEndMs = request.scheduledEndMs,
            recurrence = request.recurrence,
            recurringRuleId = request.recurringRuleId,
            status = RecordingStatus.SCHEDULED,
            scheduleEnabled = true,
            priority = request.priority,
            alarmStartAtMs = request.scheduledStartMs,
            createdAt = now,
            updatedAt = now
        )
    }

    private suspend fun spawnNextRecurringRunIfNeeded(run: RecordingRunEntity) {
        if (run.recurrence == RecordingRecurrence.NONE || run.recurringRuleId.isNullOrBlank()) return
        val scheduledRuns = recordingRunDao.getByStatus(RecordingStatus.SCHEDULED)
        val activeRuns = (scheduledRuns + recordingRunDao.getRecordingRuns()).distinctBy { it.id }
        val lookAheadLimit = when (run.recurrence) {
            RecordingRecurrence.DAILY -> 366
            RecordingRecurrence.WEEKLY -> 104
            RecordingRecurrence.NONE -> 0
        }

        for (occurrenceIndex in 1..lookAheadLimit) {
            val nextWindow = nextRecurringWindow(run, occurrenceIndex) ?: return
            val nextStart = nextWindow.first
            val nextEnd = nextWindow.second
            val overlappingPending = scheduledRuns.any {
                it.recurringRuleId == run.recurringRuleId && it.scheduledStartMs == nextStart
            }
            if (overlappingPending) return

            val conflict = activeRuns.findRecordingRunConflict(
                candidateStartMs = nextStart,
                candidateEndMs = nextEnd,
                statuses = setOf(RecordingStatus.SCHEDULED, RecordingStatus.RECORDING),
                ignoredRunId = run.id
            )
            if (conflict != null) {
                val conflictTitle = conflict.programTitle ?: conflict.channelName
                val skippedRun = run.toConflictFailure(
                    conflictStartMs = nextStart,
                    conflictEndMs = nextEnd,
                    reason = "Skipped recurring occurrence because it conflicts with '$conflictTitle'."
                )
                recordingRunDao.insert(skippedRun)
                continue
            }

            val now = System.currentTimeMillis()
            val nextRun = run.copy(
                id = UUID.randomUUID().toString(),
                status = RecordingStatus.SCHEDULED,
                scheduledStartMs = nextStart,
                scheduledEndMs = nextEnd,
                sourceType = RecordingSourceType.UNKNOWN,
                resolvedUrl = null,
                headersJson = "{}",
                userAgent = null,
                expirationTime = null,
                providerLabel = null,
                outputUri = null,
                outputDisplayPath = null,
                bytesWritten = 0L,
                averageThroughputBytesPerSecond = 0L,
                retryCount = 0,
                lastProgressAtMs = null,
                failureCategory = RecordingFailureCategory.NONE,
                failureReason = null,
                terminalAtMs = null,
                startedAtMs = null,
                endedAtMs = null,
                alarmStartAtMs = nextStart,
                alarmStopAtMs = null,
                createdAt = now,
                updatedAt = now
            )
            recordingRunDao.insert(nextRun)
            alarmScheduler.scheduleStart(nextRun.id, nextRun.scheduledStartMs)
            return
        }

        Log.w(TAG, "Unable to find a non-conflicting future slot for recurring recording ${run.id}")
    }

    private fun nextRecurringWindow(run: RecordingRunEntity, occurrencesAhead: Int = 1): Pair<Long, Long>? {
        if (run.scheduledEndMs <= run.scheduledStartMs) return null

        val zoneId = ZoneId.systemDefault()
        val startZoned = Instant.ofEpochMilli(run.scheduledStartMs).atZone(zoneId)
        val nextStartZoned = when (run.recurrence) {
            RecordingRecurrence.NONE -> return null
            RecordingRecurrence.DAILY -> startZoned.plusDays(occurrencesAhead.toLong())
            RecordingRecurrence.WEEKLY -> startZoned.plusWeeks(occurrencesAhead.toLong())
        }
        val recordingDuration = Duration.ofMillis(run.scheduledEndMs - run.scheduledStartMs)
        val nextStartMs = nextStartZoned.toInstant().toEpochMilli()
        val nextEndMs = nextStartZoned.plus(recordingDuration).toInstant().toEpochMilli()
        return nextStartMs to nextEndMs
    }

    private suspend fun validateRecordingWindow(startMs: Long, endMs: Long, providerId: Long): String? {
        val storage = ensureStorageStateSync()
        val overlapping = recordingRunDao.getOverlapping(startMs, endMs)
            .filter { it.status == RecordingStatus.SCHEDULED || it.status == RecordingStatus.RECORDING }
            .filter { it.scheduleEnabled }
        if (overlapping.size >= storage.maxSimultaneousRecordings) {
            val title = overlapping.firstOrNull()?.programTitle ?: overlapping.firstOrNull()?.channelName.orEmpty()
            return "Recording conflicts with an existing active recording for $title."
        }
        val providerMaxConnections = providerDao.getById(providerId)?.maxConnections ?: Int.MAX_VALUE
        if (overlapping.count { it.providerId == providerId } >= providerMaxConnections) {
            return "Recording exceeds the provider connection limit for this account."
        }
        return null
    }

    private suspend fun registerActiveJob(id: String, job: Job) {
        activeJobsMutex.withLock { activeJobs[id] = job }
    }

    private suspend fun removeActiveJob(id: String): Job? =
        activeJobsMutex.withLock { activeJobs.remove(id) }

    private suspend fun cancelActiveJob(id: String) {
        removeActiveJob(id)?.cancel()
    }

    private suspend fun isActiveJob(id: String): Boolean =
        activeJobsMutex.withLock { activeJobs[id]?.isActive == true }

    private suspend fun observeActiveRecordingCountSync(): Int =
        activeJobsMutex.withLock { activeJobs.values.count { it.isActive } }

    private fun existingOutputTarget(run: RecordingRunEntity): RecordingOutputTarget =
        when {
            !run.outputUri.isNullOrBlank() -> RecordingOutputTarget.DocumentTarget(
                uri = android.net.Uri.parse(run.outputUri),
                displayPath = run.outputDisplayPath
            )
            !run.outputDisplayPath.isNullOrBlank() -> RecordingOutputTarget.FileTarget(File(run.outputDisplayPath))
            else -> throw IllegalStateException("Recording output target is unavailable.")
        }

    private fun RecordingRunEntity.toStandaloneDomain(): RecordingItem = RecordingItem(
        id = id,
        scheduleId = scheduleId,
        providerId = providerId,
        channelId = channelId,
        channelName = channelName,
        streamUrl = streamUrl,
        scheduledStartMs = scheduledStartMs,
        scheduledEndMs = scheduledEndMs,
        programTitle = programTitle,
        outputPath = outputDisplayPath,
        outputUri = outputUri,
        outputDisplayPath = outputDisplayPath,
        recurrence = recurrence,
        recurringRuleId = recurringRuleId,
        status = status,
        sourceType = sourceType,
        bytesWritten = bytesWritten,
        averageThroughputBytesPerSecond = averageThroughputBytesPerSecond,
        retryCount = retryCount,
        lastProgressAtMs = lastProgressAtMs,
        failureCategory = failureCategory,
        scheduleEnabled = scheduleEnabled,
        priority = priority,
        failureReason = failureReason,
        terminalAtMs = terminalAtMs
    )

}
