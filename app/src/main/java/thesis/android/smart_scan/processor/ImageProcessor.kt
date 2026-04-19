package thesis.android.smart_scan.processor

import android.content.Context
import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.ImageDescriptionService
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.TextClassifierService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    suspend fun process(context: Context, uri: Uri) {
        Log.d(TAG, "Bắt đầu xử lý ảnh: $uri")

        val textOCR = OCRService.recognizeFromUri(uri)
        Log.d(TAG, "OCR xong — $textOCR")

        val classifierEntities = if (textOCR.isNotBlank()) {
            TextClassifierService.extractMetadata(textOCR)
        } else {
            emptyList()
        }
        val textClassifierJson = TextClassifierService.encodeEntityResults(classifierEntities)
        Log.d(TAG, "TextClassifier: ${classifierEntities.size} thực thể")

        if (textOCR.isBlank()) {
            Log.w(TAG, "Ảnh không chứa văn bản, dừng xử lý.")
            return
        }

//        val textToEmbed = try {
//            val languageTag = LanguageIdentifyService.identify(text)
//            Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")
//            val translated = TranslateService.translate(text, languageTag)
//            Log.i(TAG, "Dịch xong [$languageTag → en]: Gốc='$text', Dịch='$translated'")
//            translated
//        } catch (e: Exception) {
//            Log.w(TAG, "Nhận dạng ngôn ngữ thất bại, dùng văn bản gốc để embedding.", e)
//            text
//        }

        val description = ImageDescriptionService.describeImage(context, uri)
        val descriptionText = description.getOrNull()?.trim().orEmpty()
        Log.d(TAG, "Mô tả ảnh: $descriptionText")

        val embeddingOCR = TextEmbeddingService.embedText(textOCR)
        Log.d(TAG, "Embedding xong — size=${embeddingOCR.size}")

        val embeddingDescription = descriptionText.takeIf { it.isNotBlank() }?.let {
            TextEmbeddingService.embedText(it)
        }

        ObjectBoxRepository.put(
            Image(
                uri = uri,
                embeddingOCR = embeddingOCR,
                embeddingDescription = embeddingDescription,
                ocrText = textOCR,
                textClassifierJson = textClassifierJson,
                imageDescription = descriptionText.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Đã lưu Image vào ObjectBox.")
    }
}
