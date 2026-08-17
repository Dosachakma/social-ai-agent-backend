package com.example.data.worker

import android.content.Context
import androidx.work.*
import com.example.data.model.AppResult
import com.example.data.scheduler.DefaultSchedulerService
import com.example.data.scheduler.SchedulerService
import java.util.concurrent.TimeUnit

class ScheduledPostWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Default to DefaultSchedulerService for runtime resolution
    var schedulerService: SchedulerService = DefaultSchedulerService()

    override suspend fun doWork(): Result {
        val postId = inputData.getString(KEY_POST_ID) 
            ?: return Result.failure(Data.Builder().putString(KEY_RESULT_MESSAGE, "Missing post ID").build())

        val executionResult = schedulerService.executeScheduledPost(postId)

        return when (executionResult) {
            is AppResult.Success -> {
                val output = Data.Builder()
                    .putString(KEY_RESULT_MESSAGE, "Mock execution completed.")
                    .build()
                Result.success(output)
            }
            is AppResult.Error -> {
                val errorMsg = executionResult.error.message
                val errorCode = executionResult.error.code

                if (errorCode == "APPROVAL_REQUIRED") {
                    Result.failure(
                        Data.Builder().putString(KEY_RESULT_MESSAGE, "Post requires user approval.").build()
                    )
                } else if (errorCode == "PERMANENT_ERROR") {
                    Result.failure(
                        Data.Builder().putString(KEY_RESULT_MESSAGE, errorMsg).build()
                    )
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val KEY_POST_ID = "key_post_id"
        const val KEY_RESULT_MESSAGE = "key_result_message"
    }
}

object WorkerScheduler {
    fun enqueuePostExecution(context: Context, postId: String, delaySeconds: Long = 0) {
        val inputData = Data.Builder()
            .putString(ScheduledPostWorker.KEY_POST_ID, postId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScheduledPostWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled_post_$postId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelPostExecution(context: Context, postId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("scheduled_post_$postId")
    }
}
