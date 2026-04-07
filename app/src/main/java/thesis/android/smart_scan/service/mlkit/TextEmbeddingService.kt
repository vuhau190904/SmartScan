package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TextEmbeddingService {

    private const val TAG = "TextEmbeddingService"
    private const val MODEL_PATH = "thesis/android/smart_scan/assets/universal_sentence_encoder.tflite"

    private var textEmbedder: TextEmbedder? = null

    fun init(context: Context) {
        if (textEmbedder != null) return
        val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_PATH).build()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
        textEmbedder = TextEmbedder.createFromOptions(context.applicationContext, options)
        Log.d(TAG, "TextEmbedder khởi tạo thành công.")
    }

    suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.Default) {
        Log.d(TAG, "embedText: '$text'")
        val embedder = textEmbedder
            ?: throw IllegalStateException("TextEmbeddingService chưa được khởi tạo. Gọi init() trước.")

        val result = embedder.embed(text)
        result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
            ?: throw Exception("Không thể tạo embedding cho văn bản này.")
    }
}
