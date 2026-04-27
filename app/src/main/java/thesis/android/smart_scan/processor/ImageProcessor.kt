package thesis.android.smart_scan.processor

import android.content.Context
import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.ImageDescriptionService
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.ObjectExtractionService
import thesis.android.smart_scan.service.mlkit.TextClassifierService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService
import java.util.Locale

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
        Log.d(TAG, "TextClassifier: ${classifierEntities}")

        if (textOCR.isBlank()) {
            Log.d(TAG, "OCR rỗng — vẫn tiếp tục index ảnh và phân collection theo object.")
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

        val image = Image(
            uri = uri,
            embeddingOCR = embeddingOCR,
            embeddingDescription = embeddingDescription,
            ocrText = textOCR,
            textClassifierJson = textClassifierJson,
            imageDescription = descriptionText.ifBlank { null },
            updatedAt = System.currentTimeMillis()
        )
        val imageId = ObjectBoxRepository.put(image)

        assignImageToObjectCollections(uri, imageId)
    }

    private suspend fun assignImageToObjectCollections(uri: Uri, imageId: Long) {
        val objectsResult = runCatching { ObjectExtractionService.detectObjects(uri) }
            .getOrElse { e ->
                Log.w(TAG, "Object extraction không chạy được: ${e.message}", e)
                return
            }

        val objects = objectsResult.getOrElse { e ->
            Log.w(TAG, "detectObjects thất bại: ${e.message}", e)
            return
        }

        if (objects.isEmpty()) {
            Log.d(TAG, "Không có object nào để gán collection.")
            return
        }

        val distinctLabels = objects
            .map { it.label.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }

        for (label in distinctLabels) {
            val collection = ObjectBoxRepository.getOrCreateCollectionForLabel(label)
            if (collection == null) {
                Log.w(TAG, "Bỏ qua nhãn rỗng sau chuẩn hóa: '$label'")
                continue
            }
            val added = ObjectBoxRepository.addImageToCollection(imageId, collection.id)
            Log.d(
                TAG,
                "Collection '${collection.name}' (id=${collection.id}): add image $imageId → ${if (added) "ok" else "đã có hoặc lỗi"}"
            )
        }
    }
}
