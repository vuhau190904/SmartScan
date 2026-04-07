package thesis.android.smart_scan.service.mlkit

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object TranslateService {

    private const val TAG = "TranslateService"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = RemoteModelManager.getInstance()
    private val downloadConditions = DownloadConditions.Builder().build()
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    fun init(userLanguageCode: String) {
        serviceScope.launch {
            try { ensureModelDownloaded(TranslateLanguage.ENGLISH) }
            catch (e: Exception) { Log.w(TAG, "Tải model 'en' thất bại khi khởi tạo", e) }
        }
        val userLang = TranslateLanguage.fromLanguageTag(userLanguageCode)
        if (userLang == null) {
            Log.w(TAG, "Không tìm thấy TranslateLanguage cho tag='$userLanguageCode'.")
        } else if (userLang != TranslateLanguage.ENGLISH) {
            serviceScope.launch {
                try { ensureModelDownloaded(userLang) }
                catch (e: Exception) { Log.w(TAG, "Tải model '$userLang' thất bại khi khởi tạo", e) }
            }
        }
    }

    suspend fun translate(text: String, sourceLanguageTag: String): String {
        if (text.isBlank()) return ""

        if (sourceLanguageTag == TranslateLanguage.ENGLISH) {
            Log.d(TAG, "Văn bản đã là tiếng Anh, bỏ qua dịch.")
            return text
        }

        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguageTag)
            ?: throw Exception("ML Kit không hỗ trợ dịch ngôn ngữ: $sourceLanguageTag")

        ensureModelDownloaded(sourceLang)
        return doTranslate(sourceLang, text)
    }

    private suspend fun doTranslate(sourceLang: String, text: String): String =
        suspendCancellableCoroutine { cont ->
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
                            val result = translated ?: run {
                                Log.w(TAG, "ML Kit trả về null, dùng text gốc: $text")
                                text
                            }
                            Log.d(TAG, "Dịch thành công [$sourceLang → en]: $result")
                            cont.resume(result)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Dịch thất bại", e)
                            cont.resumeWithException(e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Tải model '$sourceLang' thất bại", e)
                    cont.resumeWithException(e)
                }
        }

    private suspend fun ensureModelDownloaded(languageCode: String): Unit =
        suspendCancellableCoroutine { cont ->
            val model = TranslateRemoteModel.Builder(languageCode).build()
            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    if (isDownloaded) {
                        Log.d(TAG, "Model '$languageCode' đã có sẵn.")
                        cont.resume(Unit)
                    } else {
                        Log.d(TAG, "Model '$languageCode' chưa có, đang tải...")
                        modelManager.download(model, downloadConditions)
                            .addOnSuccessListener {
                                Log.d(TAG, "Model '$languageCode' tải xong.")
                                cont.resume(Unit)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Tải model '$languageCode' thất bại", e)
                                cont.resumeWithException(e)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Kiểm tra model '$languageCode' thất bại", e)
                    cont.resumeWithException(e)
                }
        }
}
