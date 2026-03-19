package thesis.android.smart_scan.service.mlkit

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslateService(userLanguageCode: String) {

    companion object {
        private const val TAG = "TranslateService"
    }

    private val modelManager = RemoteModelManager.getInstance()

    private val downloadConditions = DownloadConditions.Builder().build()

    private val translatorCache = mutableMapOf<String, Translator>()

    init {
        ensureModelDownloaded(TranslateLanguage.ENGLISH)
        val userLang = TranslateLanguage.fromLanguageTag(userLanguageCode)
        if (userLang == null) {
            Log.w(TAG, "Không tìm thấy TranslateLanguage cho tag='$userLanguageCode'.")
        } else if(userLang != TranslateLanguage.ENGLISH){
            ensureModelDownloaded(userLang)
        }
    }

    fun translate(
        text: String,
        sourceLanguageTag: String,
        onSuccess: (translatedText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        if (text.isBlank()) {
            onSuccess("")
            return
        }

        if (sourceLanguageTag == TranslateLanguage.ENGLISH) {
            Log.d(TAG, "Văn bản đã là tiếng Anh, bỏ qua dịch.")
            onSuccess(text)
            return
        }

        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguageTag)
        if (sourceLang == null) {
            onFailure(Exception("ML Kit không hỗ trợ dịch ngôn ngữ: $sourceLanguageTag"))
            return
        }

        ensureModelDownloaded(
            languageCode = sourceLang,
            onReady = { doTranslate(sourceLang, text, onSuccess, onFailure) },
            onError = onFailure
        )
    }

    fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        Log.d(TAG, "TranslateService đã đóng.")
    }

    private fun doTranslate(
        sourceLang: String,
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val translator = translatorCache.getOrPut(sourceLang) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            )
        }

        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        Log.d(TAG, "Dịch thành công [$sourceLang → en]: $translated")
                        onSuccess(translated)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Dịch thất bại", e)
                        onFailure(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Tải model '$sourceLang' thất bại", e)
                onFailure(e)
            }
    }

    private fun ensureModelDownloaded(
        languageCode: String,
        onReady: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val model = TranslateRemoteModel.Builder(languageCode).build()
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    Log.d(TAG, "Model '$languageCode' đã có sẵn.")
                    onReady?.invoke()
                } else {
                    Log.d(TAG, "Model '$languageCode' chưa có, đang tải...")
                    modelManager.download(model, downloadConditions)
                        .addOnSuccessListener {
                            Log.d(TAG, "Model '$languageCode' tải xong.")
                            onReady?.invoke()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Tải model '$languageCode' thất bại", e)
                            onError?.invoke(e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kiểm tra model '$languageCode' thất bại", e)
                onError?.invoke(e)
            }
    }
}
