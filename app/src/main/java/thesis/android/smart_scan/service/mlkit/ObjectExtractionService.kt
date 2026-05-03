package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import thesis.android.smart_scan.config.AppConfig

data class DetectedObject(
    val label: String,
    val score: Float,
)

object ObjectExtractionService {

    private const val TAG = "ObjectExtractionService"

    private var appContext: Context? = null
    private var imageLabeler: ImageLabeler? = null

    fun init(context: Context, appConfig: AppConfig = AppConfig()) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        try {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(appConfig.objectDetectorScoreThreshold)
                .build()

            imageLabeler = ImageLabeling.getClient(options)
            Log.d(TAG, "ImageLabeler khởi tạo thành công.")

        } catch (e: Exception) {
            Log.e(TAG, "KHÔNG THỂ khởi tạo ImageLabeler: ${e.message}", e)
            imageLabeler = null
            // Thông báo lỗi ra UI nếu cần
        }
    }

    suspend fun detectObjects(uri: Uri): Result<List<DetectedObject>> = withContext(Dispatchers.Default) {
        val labeler = imageLabeler
            ?: return@withContext Result.failure(IllegalStateException("ImageLabeler chưa được khởi tạo."))
        val ctx = appContext
            ?: return@withContext Result.failure(IllegalStateException("Context không khả dụng."))

        return@withContext try {
            val inputImage = InputImage.fromFilePath(ctx, uri)

            val labels = labeler.process(inputImage).await()

            val list = labels.map { label ->
                DetectedObject(
                    label = label.text,
                    score = label.confidence
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "detectObjects() thất bại", e)
            Result.failure(e)
        }
    }

    fun close() {
        imageLabeler?.close()
        imageLabeler = null
        appContext = null
    }
}