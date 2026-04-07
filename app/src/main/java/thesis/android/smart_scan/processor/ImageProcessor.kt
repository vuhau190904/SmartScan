package thesis.android.smart_scan.processor

import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    suspend fun process(uri: Uri) {
        Log.d(TAG, "Bắt đầu xử lý ảnh: $uri")

        val text = OCRService.recognizeFromUri(uri)
        Log.d(TAG, "OCR xong — $text")

        if (text.isBlank()) {
            Log.w(TAG, "Ảnh không chứa văn bản, dừng xử lý.")
            return
        }

        val textToEmbed = try {
            val languageTag = LanguageIdentifyService.identify(text)
            Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")
            val translated = TranslateService.translate(text, languageTag)
            Log.i(TAG, "Dịch xong [$languageTag → en]: Gốc='$text', Dịch='$translated'")
            translated
        } catch (e: Exception) {
            Log.w(TAG, "Nhận dạng ngôn ngữ thất bại, dùng văn bản gốc để embedding.", e)
            text
        }

        val embedding = TextEmbeddingService.embedText(text)
        Log.d(TAG, "Embedding xong — size=${embedding.size}")

        ObjectBoxRepository.put(Image(uri = uri, embedding = embedding))
        Log.d(TAG, "Đã lưu Image vào ObjectBox.")
    }
}
