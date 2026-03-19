package thesis.android.smart_scan.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import thesis.android.smart_scan.config.AppConfig
import thesis.android.smart_scan.processor.ImageProcessor
import thesis.android.smart_scan.util.Constant
import androidx.core.net.toUri

class BackgroundProcessingWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "BackgroundProcessingWorker"
    }

    override fun doWork(): Result {
        val imageUriString = inputData.getString(Constant.IMAGE_URI)
            ?: return Result.failure()

        val uri = imageUriString.toUri()
        Log.d(TAG, "Worker bắt đầu xử lý ảnh: $uri")

        return try {
            val config = AppConfig()
            val processor = ImageProcessor(applicationContext, config)

            processor.process(uri)

            Log.d(TAG, "Worker đã hoàn thành xử lý ảnh thành công.")
            
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong quá trình xử lý ngầm: ${e.message}", e)
            
            Result.retry()
        }
    }
}