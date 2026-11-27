package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AcademyManagerApp

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AcademyManagerApp ?: return Result.failure()
        val ok = app.cloudSyncManager.pushPendingChanges()
        return if (ok) Result.success() else Result.retry()
    }
}
