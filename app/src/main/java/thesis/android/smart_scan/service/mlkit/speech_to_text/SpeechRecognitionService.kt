package thesis.android.smart_scan.service.mlkit.speech_to_text

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import thesis.android.smart_scan.R
import java.util.Locale

object SpeechRecognitionService {

    private const val TAG = "SpeechRecognitionService"

    @Volatile
    private var speechLocale: Locale = Locale.forLanguageTag("vi-VN")

    fun init(locale: Locale = Locale.forLanguageTag("vi-VN")) {
        speechLocale = normalizeSpeechLocale(locale)
        Log.d(TAG, "Đã cấu hình ngôn ngữ nhận dạng: ${speechLocale.toLanguageTag()}")
    }

    suspend fun recognizeFromMic(delegate: SpeechRecognitionDelegate): Result<String> {
        val activity = delegate.activity
        if (!SpeechRecognizer.isRecognitionAvailable(activity.applicationContext)) {
            return Result.failure(IllegalStateException("Thiết bị không hỗ trợ nhận dạng giọng nói"))
        }

        val intent = createRecognizerIntent(activity)
        if (intent.resolveActivity(activity.packageManager) == null) {
            return Result.failure(IllegalStateException("Không có ứng dụng xử lý nhận dạng giọng nói"))
        }

        return withContext(Dispatchers.Main.immediate) {
            try {
                val activityResult = delegate.startForResult(intent)
                activityResult.toSpeechResult()
            } catch (e: Exception) {
                Log.e(TAG, "recognizeFromMic() thất bại", e)
                Result.failure(e)
            }
        }
    }

    private fun createRecognizerIntent(activity: Context): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            val languageTag = speechLocale.toLanguageTag()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            // Force system recognizer to prefer offline pack (no network dependency).
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Give the recognizer more time before deciding user stopped talking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                activity.getString(R.string.voice_listening)
            )
        }
    }

    private fun normalizeSpeechLocale(input: Locale): Locale {
        if (input.country.isNotBlank()) return input
        return when (input.language.lowercase(Locale.ROOT)) {
            "en" -> Locale.forLanguageTag("en-US")
            "vi" -> Locale.forLanguageTag("vi-VN")
            "ja" -> Locale.forLanguageTag("ja-JP")
            "ko" -> Locale.forLanguageTag("ko-KR")
            "zh" -> Locale.forLanguageTag("zh-CN")
            "fr" -> Locale.forLanguageTag("fr-FR")
            "de" -> Locale.forLanguageTag("de-DE")
            "es" -> Locale.forLanguageTag("es-ES")
            else -> input
        }
    }

    private fun ActivityResult.toSpeechResult(): Result<String> {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = results
                    ?.firstOrNull { !it.isNullOrBlank() }
                    ?.trim()
                    .orEmpty()
                Result.success(text)
            }
            else -> Result.success("")
        }
    }
}
