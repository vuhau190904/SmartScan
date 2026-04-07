package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OCRService {

    private const val TAG = "OCRService"

    private lateinit var appContext: Context
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun recognizeFromUri(imageUri: Uri): String = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(appContext, imageUri)
        } catch (e: IOException) {
            Log.e(TAG, "Không thể đọc ảnh từ uri=$imageUri", e)
            cont.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d(TAG, "OCR thành công — ${visionText.text}")
                cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR thất bại", e)
                cont.resumeWithException(e)
            }
    }
}
