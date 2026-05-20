package thesis.android.smart_scan.worker

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import thesis.android.smart_scan.processor.ImageProcessor
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.ImageEventBus
import thesis.android.smart_scan.util.PerformanceLogger

class BackgroundProcessingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackgroundProcessingWorker"
    }

    override suspend fun doWork(): Result {
        val imageUriString = inputData.getString(Constant.IMAGE_URI)
            ?: return Result.failure()
        val processingStartedAtMs = inputData.getLong(
            Constant.PROCESSING_START_TIME_MS,
            PerformanceLogger.now()
        )

        val uri = imageUriString.toUri()
        Log.d(TAG, "Worker bắt đầu xử lý ảnh: $uri")

        return try {
            ImageProcessor.process(applicationContext, uri, processingStartedAtMs)
            Log.d(TAG, "Worker đã hoàn thành xử lý ảnh thành công.")
            ImageEventBus.notifyNewImage(uri, processingStartedAtMs)
            PerformanceLogger.logDuration(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "pipeline_until_worker_notify",
                processingStartedAtMs,
                "uri=$uri"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong quá trình xử lý ngầm: ${e.message}", e)
            Result.retry()
        }
    }
}
