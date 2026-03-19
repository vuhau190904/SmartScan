package thesis.android.smart_scan.service.mlkit

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions

class LanguageIdentifyService {

    companion object {
        private const val TAG = "LanguageIdentifyService"

        private const val CONFIDENCE_THRESHOLD = 0.3f
    }

    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .build()
    )

    fun identify(
        text: String,
        onSuccess: (languageTag: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        if (text.isBlank()) {
            Log.w(TAG, "Văn bản rỗng, bỏ qua nhận dạng ngôn ngữ.")
            onFailure(Exception("\"Văn bản rỗng, bỏ qua nhận dạng ngôn ngữ.\""))
            return
        }

        identifier.identifyLanguage(text)
            .addOnSuccessListener { languageTag ->
                Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag (text length=${text.length})")
                onSuccess(languageTag)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Nhận dạng ngôn ngữ thất bại", e)
                onFailure(e)
            }
    }

    fun close() {
        identifier.close()
    }
}
