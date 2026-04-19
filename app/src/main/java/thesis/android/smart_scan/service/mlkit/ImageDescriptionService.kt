package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ImageDescriptionService {

    private const val TAG = "ImageDescriptionService"

    private lateinit var describer: ImageDescriber

    suspend fun init(context: Context) {
        describer = ImageDescription.getClient(
            ImageDescriberOptions.builder(context).build()
        )
        prepareModel(describer)
        Log.d(TAG, "ImageDescriptionService khởi tạo thành công.")
    }

    suspend fun describeImage(context: Context, uri: Uri): Result<String> {
        val bitmap = run {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        }

        return try {
            val request = ImageDescriptionRequest.builder(bitmap).build()
            val result = describer.runInference(request).suspendAwait()
            Log.d(TAG, "Mô tả ảnh thành công: ${result.description}")
            Result.success(result.description)
        } catch (e: Exception) {
            Log.e(TAG, "describeImage() thất bại", e)
            Result.failure(e)
        }
    }

    fun close() {
        describer.close()
    }

    private suspend fun prepareModel(client: ImageDescriber): Boolean {
        val status = try {
            client.checkFeatureStatus().suspendAwait()
        } catch (e: GenAiException) {
            Log.e(
                TAG,
                "checkFeatureStatus lỗi GenAI (errorCode=${e.errorCode}, message=${e.message})",
                e
            )
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Không kiểm tra được trạng thái model Image Description", e)
            return false
        }
        Log.d(TAG, "Feature status: $status")

        return when (status) {
            FeatureStatus.AVAILABLE -> true
            FeatureStatus.UNAVAILABLE -> {
                Log.w(TAG, "Thiết bị không hỗ trợ Image Description.")
                false
            }
            FeatureStatus.DOWNLOADING, FeatureStatus.DOWNLOADABLE -> {
                waitForDownload()
                true
            }
            else -> false
        }
    }

    private suspend fun waitForDownload() =
        suspendCancellableCoroutine<Unit> { cont ->
            describer.downloadFeature(object : DownloadCallback {
                override fun onDownloadCompleted() {
                    Log.d(TAG, "Model tải xong.")
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onDownloadFailed(e: GenAiException) {
                    Log.e(TAG, "Tải model thất bại: ${e.message}", e)
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onDownloadStarted(bytesToDownload: Long) {
                    Log.d(TAG, "Bắt đầu tải model — $bytesToDownload bytes")
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {}
            })
        }

    private suspend fun <T> ListenableFuture<T>.suspendAwait(): T =
        suspendCancellableCoroutine { cont ->
            addListener({
                try {
                    cont.resume(get())
                } catch (e: ExecutionException) {
                    cont.resumeWithException(e.cause ?: e)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, { command -> command.run() })
            cont.invokeOnCancellation { cancel(true) }
        }
}
