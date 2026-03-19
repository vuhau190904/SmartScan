package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

class OCRService(private val context: Context) {

    companion object {
        private const val TAG = "OCRService"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognizeFromUri(
        imageUri: Uri,
        onSuccess: (text: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val image: InputImage = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (e: IOException) {
            Log.e(TAG, "Không thể đọc ảnh từ uri=$imageUri", e)
            onFailure(e)
            return
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d(TAG, "OCR thành công — ${visionText.text}")
                onSuccess(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR thất bại", e)
                onFailure(e)
            }
    }

    fun close() {
        recognizer.close()
    }
}
