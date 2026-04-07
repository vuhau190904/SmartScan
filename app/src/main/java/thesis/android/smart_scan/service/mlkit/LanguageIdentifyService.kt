package thesis.android.smart_scan.service.mlkit

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LanguageIdentifyService {

    private const val TAG = "LanguageIdentifyService"
    private const val CONFIDENCE_THRESHOLD = 0.3f

    private val identifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build()
        )
    }

    suspend fun identify(text: String): String = suspendCancellableCoroutine { cont ->
        if (text.isBlank()) {
            Log.w(TAG, "Văn bản rỗng, bỏ qua nhận dạng ngôn ngữ.")
            cont.resumeWithException(Exception("Văn bản rỗng, bỏ qua nhận dạng ngôn ngữ."))
            return@suspendCancellableCoroutine
        }

        identifier.identifyLanguage(text)
            .addOnSuccessListener { languageTag ->
                val tag = languageTag ?: "und"
                Log.d(TAG, "Ngôn ngữ nhận dạng: $tag (text length=${text.length})")
                cont.resume(tag)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Nhận dạng ngôn ngữ thất bại", e)
                cont.resumeWithException(e)
            }
    }
}
