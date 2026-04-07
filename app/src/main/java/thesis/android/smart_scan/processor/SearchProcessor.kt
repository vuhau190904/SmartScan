package thesis.android.smart_scan.processor

import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService

object SearchProcessor {

    private const val TAG = "SearchProcessor"

    suspend fun search(query: String): List<Uri> {
        if (query.isBlank()) {
            Log.w(TAG, "Query trống, dừng tìm kiếm.")
            return emptyList()
        }

        val queryToEmbed = try {
            val languageTag = LanguageIdentifyService.identify(query)
            Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")
            val translated = TranslateService.translate(query, languageTag)
            Log.i(TAG, "Dịch xong [$languageTag → en]: Gốc='$query', Dịch='$translated'")
            translated
        } catch (e: Exception) {
            Log.w(TAG, "Nhận dạng ngôn ngữ thất bại, dùng query gốc để tìm kiếm.", e)
            query
        }

        val embedding = TextEmbeddingService.embedText(query)
        Log.d(TAG, "Embedding xong — size=${embedding.size}")

        return ObjectBoxRepository.search(embedding, 10)
    }
}
