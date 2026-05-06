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

        val textToEmbed = try {
            val languageTag = LanguageIdentifyService.identify(textOCR)
            Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")
            val translated = TranslateService.translate(textOCR, languageTag)
            Log.i(TAG, "Dịch xong [$languageTag → en]: Gốc='$textOCR', Dịch='$translated'")
            translated
        } catch (e: Exception) {
            Log.w(TAG, "Nhận dạng ngôn ngữ thất bại, dùng văn bản gốc để embedding.", e)
            textOCR
        }

        val embeddingOCR = TextEmbeddingService.embedText(textToEmbed)
        Log.d(TAG, "Embedding xong — size=${embeddingOCR.size}")

        val description = ImageDescriptionService.describeImage(context, uri)
        val descriptionText = description.getOrNull()?.trim().orEmpty()
        Log.d(TAG, "Mô tả ảnh: $descriptionText")

        val collections = detectObject(uri)

        val objectsSentence = if (collections.isNotEmpty()) {
            "The image contains: ${collections.joinToString(", ")}."
        } else {
            ""
        }

        val finalDescription = listOf(objectsSentence, descriptionText)
            .filter { it.isNotBlank() }
            .joinToString(". ")
            .takeIf { it.isNotBlank() }

        val embeddingDescription = finalDescription?.let {
            TextEmbeddingService.embedText(it)
        }

        val image = Image(
            uri = uri,
            embeddingOCR = embeddingOCR,
            embeddingDescription = embeddingDescription,
            ocrText = textOCR,
            textClassifierJson = textClassifierJson,
            imageDescription = finalDescription,
            updatedAt = System.currentTimeMillis()
        )
        val imageId = ObjectBoxRepository.put(image)

        assignImageToObjectCollections(collections, imageId)
    }

    private suspend fun detectObject(uri: Uri): List<String> {
        val objectsResult = runCatching { ObjectExtractionService.detectObjects(uri) }
            .getOrElse { e ->
                Log.w(TAG, "Object extraction không chạy được: ${e.message}", e)
                return emptyList<String>()
            }

        val objects = objectsResult.getOrElse { e ->
            Log.w(TAG, "detectObjects thất bại: ${e.message}", e)
            return emptyList<String>()
        }

        if (objects.isEmpty()) {
            Log.d(TAG, "Không có object nào để gán collection.")
            return emptyList<String>()
        }

        return objects
            .map { it.label.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    private fun assignImageToObjectCollections(distinctLabels: List<String>, imageId: Long) {
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
