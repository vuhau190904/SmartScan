package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import thesis.android.smart_scan.config.AppConfig
import java.util.Locale

data class DetectedObject(
    val label: String,
    val score: Float,
)

object ObjectExtractionService {

    private const val TAG = "ObjectExtractionService"

    private const val MODEL_ASSET_PATH = "efficientdet_lite0.tflite"

    private var appContext: Context? = null
    private var objectDetector: ObjectDetector? = null

    fun init(context: Context, appConfig: AppConfig = AppConfig()) {
        val applicationContext = context.applicationContext
        appContext = applicationContext

        runCatching {
            val displayLocale = appConfig.objectDetectorDisplayNamesLocales
                .firstOrNull { it.isNotBlank() }
                ?: Locale.getDefault().toLanguageTag()

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setScoreThreshold(appConfig.objectDetectorScoreThreshold)
                .setDisplayNamesLocale(displayLocale)
                .build()

            objectDetector = ObjectDetector.createFromOptions(applicationContext, options)
            Log.d(TAG, "ObjectDetector khởi tạo thành công.")
        }.onFailure { e ->
            Log.e(TAG, "KHÔNG THỂ khởi tạo ObjectDetector: ${e.message}", e)
            objectDetector = null
            // Thông báo lỗi này ra UI nếu cần, nhưng App sẽ không bị Crash
        }
    }

    suspend fun detectObjects(uri: Uri): Result<List<DetectedObject>> = withContext(Dispatchers.Default) {
        val detector = objectDetector
            ?: return@withContext Result.failure(IllegalStateException("ObjectDetector chưa được khởi tạo."))
        val ctx = appContext
            ?: return@withContext Result.failure(IllegalStateException("Context không khả dụng."))

        val bitmap = try {
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không decode được ảnh từ URI", e)
            return@withContext Result.failure(e)
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        return@withContext try {
            val detectionResult = detector.detect(mpImage)
            val list = detectionResult.detections().mapNotNull { detection ->
                val category = detection.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val label = category.displayName().takeIf { it.isNotBlank() } ?: category.categoryName()
                DetectedObject(
                    label = label,
                    score = category.score(),
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "detectObjects() thất bại", e)
            Result.failure(e)
        } finally {
            mpImage.close()
        }
    }

    fun close() {
        objectDetector?.close()
        objectDetector = null
        appContext = null
    }
}
