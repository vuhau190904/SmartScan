package thesis.android.smart_scan.processor

import android.content.Context
import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.ImageDescriptionService
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.ObjectExtractionService
import thesis.android.smart_scan.service.mlkit.TextClassifierService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService
import thesis.android.smart_scan.util.PerformanceLogger
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    suspend fun process(
        context: Context,
        uri: Uri,
        processingStartedAtMs: Long = PerformanceLogger.now()
    ) = coroutineScope {
        val processorStartedAtMs = PerformanceLogger.now()
        Log.d(TAG, "Bắt đầu xử lý ảnh: $uri")

        try {
            PerformanceLogger.measure(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "step_media_store_read",
                "uri=$uri"
            ) {
                runCatching { MediaContentRepository.getImageDetails(uri) }
                    .onFailure { e ->
                        Log.w(TAG, "Không đọc được metadata ảnh từ MediaStore: ${e.message}", e)
                    }
                    .getOrNull()
            }

            val ocrJob = async {
                PerformanceLogger.measureSuspend(
                    PerformanceLogger.TAG_PROCESSING_TIME,
                    "branch_ocr_text_pipeline",
                    "uri=$uri"
                ) {
                    val textOCR = PerformanceLogger.measureSuspend(
                        PerformanceLogger.TAG_PROCESSING_TIME,
                        "step_ocr",
                        "uri=$uri"
                    ) {
                        OCRService.recognizeFromUri(uri)
                    }
                    Log.d(TAG, "OCR xong — $textOCR")

                    val classifierEntities = PerformanceLogger.measure(
                        PerformanceLogger.TAG_PROCESSING_TIME,
                        "step_text_classifier",
                        "uri=$uri text_length=${textOCR.length}"
                    ) {
                        if (textOCR.isNotBlank()) {
                            TextClassifierService.extractMetadata(textOCR)
                        } else {
                            emptyList()
                        }
                    }
                    val textClassifierJson = TextClassifierService.encodeEntityResults(classifierEntities)
                    Log.d(TAG, "TextClassifier: ${classifierEntities}")

                    if (textOCR.isBlank()) {
                        Log.d(TAG, "OCR rỗng — vẫn tiếp tục index ảnh và phân collection theo object.")
                    }

                    val textToEmbed = try {
                        val languageTag = PerformanceLogger.measureSuspend(
                            PerformanceLogger.TAG_PROCESSING_TIME,
                            "step_language_identification",
                            "uri=$uri text_length=${textOCR.length}"
                        ) {
                            LanguageIdentifyService.identify(textOCR)
                        }
                        Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")
                        val translated = PerformanceLogger.measureSuspend(
                            PerformanceLogger.TAG_PROCESSING_TIME,
                            "step_translate_to_en",
                            "uri=$uri source_language=$languageTag text_length=${textOCR.length}"
                        ) {
                            TranslateService.translate(textOCR, languageTag)
                        }
                        Log.i(TAG, "Dịch xong [$languageTag → en]: Gốc='$textOCR', Dịch='$translated'")
                        translated
                    } catch (e: Exception) {
                        Log.w(TAG, "Nhận dạng ngôn ngữ thất bại, dùng văn bản gốc để embedding.", e)
                        textOCR
                    }

                    val embeddingOCR = PerformanceLogger.measureSuspend(
                        PerformanceLogger.TAG_PROCESSING_TIME,
                        "step_embed_ocr_text",
                        "uri=$uri text_length=${textToEmbed.length}"
                    ) {
                        TextEmbeddingService.embedText(textToEmbed)
                    }
                    Log.d(TAG, "Embedding xong — size=${embeddingOCR.size}")
                    Triple(textOCR, textClassifierJson, embeddingOCR)
                }
            }

            val imageJob = async {
                PerformanceLogger.measureSuspend(
                    PerformanceLogger.TAG_PROCESSING_TIME,
                    "branch_image_semantic_pipeline",
                    "uri=$uri"
                ) {
                    val description = PerformanceLogger.measureSuspend(
                        PerformanceLogger.TAG_PROCESSING_TIME,
                        "step_image_description",
                        "uri=$uri"
                    ) {
                        ImageDescriptionService.describeImage(context, uri)
                    }
                    val descriptionText = description.getOrNull()?.trim().orEmpty()
                    Log.d(TAG, "Mô tả ảnh: $descriptionText")

                    val collections = PerformanceLogger.measureSuspend(
                        PerformanceLogger.TAG_PROCESSING_TIME,
                        "step_image_labeling",
                        "uri=$uri"
                    ) {
                        detectObject(uri)
                    }

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
                        PerformanceLogger.measureSuspend(
                            PerformanceLogger.TAG_PROCESSING_TIME,
                            "step_embed_description_text",
                            "uri=$uri text_length=${it.length}"
                        ) {
                            TextEmbeddingService.embedText(it)
                        }
                    }
                    Triple(finalDescription, embeddingDescription, collections)
                }
            }

            val (textOCR, textClassifierJson, embeddingOCR) = ocrJob.await()
            val (finalDescription, embeddingDescription, collections) = imageJob.await()

            val image = Image(
                uri = uri,
                embeddingOCR = embeddingOCR,
                embeddingDescription = embeddingDescription,
                ocrText = textOCR,
                textClassifierJson = textClassifierJson,
                imageDescription = finalDescription,
                updatedAt = System.currentTimeMillis()
            )
            val imageId = PerformanceLogger.measure(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "step_objectbox_put",
                "uri=$uri"
            ) {
                ObjectBoxRepository.put(image)
            }

            PerformanceLogger.measure(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "step_assign_collections",
                "uri=$uri labels=${collections.size}"
            ) {
                assignImageToObjectCollections(collections, imageId)
            }
        } finally {
            PerformanceLogger.logDuration(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "image_processor_total",
                processorStartedAtMs,
                "uri=$uri mode=parallel"
            )
            PerformanceLogger.logDuration(
                PerformanceLogger.TAG_PROCESSING_TIME,
                "pipeline_total_until_process_end",
                processingStartedAtMs,
                "uri=$uri mode=parallel"
            )
        }
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
