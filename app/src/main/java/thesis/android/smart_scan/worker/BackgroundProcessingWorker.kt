package thesis.android.smart_scan.worker

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import thesis.android.smart_scan.processor.ImageProcessor
import thesis.android.smart_scan.util.Constant

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

        val uri = imageUriString.toUri()
        Log.d(TAG, "Worker bắt đầu xử lý ảnh: $uri")

        return try {
            ImageProcessor.process(uri)
            Log.d(TAG, "Worker đã hoàn thành xử lý ảnh thành công.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong quá trình xử lý ngầm: ${e.message}", e)
            Result.retry()
        }
    }
}
