package thesis.android.smart_scan.service.mlkit.speech_to_text

import android.content.Context
import android.util.Log
import android.widget.Toast
import thesis.android.smart_scan.R

object SpeechRecognitionUiHelper {

    suspend fun recognizeAndHandle(
        context: Context,
        delegate: SpeechRecognitionDelegate,
        logTag: String? = null,
        onTextReady: (String) -> Unit
    ) {
        val result = try {
            SpeechRecognitionService.recognizeFromMic(delegate)
        } catch (e: Exception) {
            if (logTag != null) {
                Log.e(logTag, "Voice recognition thất bại ngoài dự kiến", e)
            }
            Result.failure(e)
        }

        val text = result.getOrNull().orEmpty().trim()
        when {
            result.isFailure -> {
                Toast.makeText(context, context.getString(R.string.voice_not_supported), Toast.LENGTH_SHORT).show()
            }
            text.isBlank() -> {
                Toast.makeText(context, context.getString(R.string.voice_no_text), Toast.LENGTH_SHORT).show()
            }
            else -> onTextReady(text)
        }
    }
}
