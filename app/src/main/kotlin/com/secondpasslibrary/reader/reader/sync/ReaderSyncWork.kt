package com.secondpasslibrary.reader.reader.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.ConnectionProfileStore
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import com.secondpasslibrary.reader.connection.storage.WorkOfflineStore
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Repairs the durable WorkManager wakeup whenever a persisted account shell is restored. */
internal class ReaderSyncWakeupController(
    private val scheduler: ReaderPendingSyncScheduler,
    private val scope: CoroutineScope
) {
    private var account: LocalReaderAccountKey? = null
    private var job: Job? = null

    fun update(profile: ConnectionProfile?, profileId: String?) {
        val next = if (profile != null && profileId != null) {
            LocalReaderAccountKey.from(profile.serverId, profileId)
        } else {
            null
        }
        if (next == account) return
        job?.cancel()
        account = next
        job = next?.let { selected ->
            scope.launch { scheduler.ensureEnqueued(selected) }
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        account = null
    }
}

@Singleton
internal class WorkManagerReaderSyncScheduler @Inject constructor(
    private val outbox: ReaderOutboxStore,
    private val queue: ReaderSyncWorkQueue
) : ReaderPendingSyncScheduler {
    override suspend fun ensureEnqueued(account: LocalReaderAccountKey) {
        if (!outbox.hasPendingWork(account)) return
        queue.ensureEnqueued(account)
    }

    override fun cancel(account: LocalReaderAccountKey) {
        queue.cancel(account)
    }
}

internal interface ReaderSyncWorkQueue {
    fun ensureEnqueued(account: LocalReaderAccountKey)

    fun cancel(account: LocalReaderAccountKey)

    suspend fun cancelAll() = Unit
}

@Singleton
internal class WorkManagerReaderSyncWorkQueue @Inject constructor(
    @ApplicationContext context: Context
) : ReaderSyncWorkQueue {
    private val appContext = context.applicationContext

    override fun ensureEnqueued(account: LocalReaderAccountKey) {
        val request = readerSyncWorkRequest(account)
        workManager().enqueueUniqueWork(
            workName(account),
            READER_SYNC_EXISTING_WORK_POLICY,
            request
        )
    }

    override fun cancel(account: LocalReaderAccountKey) {
        workManager().cancelUniqueWork(workName(account))
    }

    override suspend fun cancelAll() {
        withContext(Dispatchers.IO) {
            workManager().cancelAllWork().result.get()
        }
    }

    private fun workManager() = WorkManager.getInstance(appContext)
}

internal class ReaderSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val execution: ReaderSyncWorkerExecution
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val expected = inputData.getString(INPUT_ACCOUNT_KEY) ?: return Result.success()
        return when (execution.execute(expected)) {
            ReaderSyncWorkerOutcome.SUCCESS -> Result.success()
            ReaderSyncWorkerOutcome.RETRY -> Result.retry()
            ReaderSyncWorkerOutcome.AUTHENTICATION_REQUIRED -> Result.failure()
        }
    }
}

internal enum class ReaderSyncWorkerOutcome {
    SUCCESS,
    RETRY,
    AUTHENTICATION_REQUIRED
}

@Singleton
internal class ReaderSyncWorkerExecution @Inject constructor(
    private val accountResolver: ReaderSyncAccountResolution,
    private val outbox: ReaderOutboxStore,
    private val reconnect: ReaderReconnectOperation,
    private val workOfflineStore: WorkOfflineStore
) {
    suspend fun execute(persistedAccountKey: String): ReaderSyncWorkerOutcome = try {
        val account = runCatching {
            LocalReaderAccountKey.fromPersistedValue(persistedAccountKey)
        }.getOrNull() ?: return ReaderSyncWorkerOutcome.SUCCESS
        val profile = accountResolver.resolve(account) ?: return ReaderSyncWorkerOutcome.SUCCESS
        if (!outbox.hasPendingWork(account)) return ReaderSyncWorkerOutcome.SUCCESS
        if (workOfflineStore.read(account.value)) return ReaderSyncWorkerOutcome.RETRY
        reconnect.reconnect(profile, account).toWorkerOutcome()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ReaderSyncWorkerOutcome.RETRY
    }
}

internal fun interface ReaderSyncAccountResolution {
    suspend fun resolve(expected: LocalReaderAccountKey): ConnectionProfile?
}

@Singleton
internal class ReaderSyncAccountResolver @Inject constructor(
    private val profileStore: ConnectionProfileStore,
    private val accountStore: PersistedAccountContextStore
) : ReaderSyncAccountResolution {
    override suspend fun resolve(expected: LocalReaderAccountKey): ConnectionProfile? {
        val profile = profileStore.read()
        val persisted = accountStore.read()
        val valid = profile != null && persisted?.matches(profile) == true &&
            LocalReaderAccountKey.from(profile.serverId, persisted.profileId) == expected
        return profile.takeIf { valid }
    }
}

@Singleton
internal class ReaderSyncWorkerFactory @Inject constructor(
    private val execution: ReaderSyncWorkerExecution
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = if (workerClassName == ReaderSyncWorker::class.java.name) {
        ReaderSyncWorker(appContext, workerParameters, execution)
    } else {
        null
    }
}

internal fun readerWorkManagerConfiguration(factory: ReaderSyncWorkerFactory): Configuration =
    Configuration.Builder().setWorkerFactory(factory).build()

private fun ReaderReconnectReport.toWorkerOutcome(): ReaderSyncWorkerOutcome = when {
    authenticationRequired -> ReaderSyncWorkerOutcome.AUTHENTICATION_REQUIRED
    transientFailure || remainingPendingWork -> ReaderSyncWorkerOutcome.RETRY
    else -> ReaderSyncWorkerOutcome.SUCCESS
}

internal fun readerSyncWorkRequest(account: LocalReaderAccountKey) =
    OneTimeWorkRequestBuilder<ReaderSyncWorker>()
        .setInputData(Data.Builder().putString(INPUT_ACCOUNT_KEY, account.value).build())
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            READER_SYNC_BACKOFF_SECONDS,
            TimeUnit.SECONDS
        )
        .addTag(workName(account))
        .build()

internal fun workName(account: LocalReaderAccountKey) = "reader-sync:${account.value}"

internal const val INPUT_ACCOUNT_KEY = "reader_sync_account_key"
internal const val READER_SYNC_BACKOFF_SECONDS = 30L
internal val READER_SYNC_EXISTING_WORK_POLICY = ExistingWorkPolicy.KEEP
