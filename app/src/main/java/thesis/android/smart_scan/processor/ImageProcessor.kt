package thesis.android.smart_scan.processor

import android.content.Context
import android.net.Uri
import android.util.Log
import thesis.android.smart_scan.config.AppConfig
import thesis.android.smart_scan.service.mlkit.LanguageIdentifyService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.TranslateService

class ImageProcessor(
    context: Context,
    config: AppConfig = AppConfig()
) {
    companion object {
        private const val TAG = "ImageProcessor"
    }

    private val ocrService = OCRService(context)
    private val languageIdentifyService = LanguageIdentifyService()
    private val translateService = TranslateService(config.userLanguage)

    fun process(uri: Uri) {
        Log.d(TAG, "Bắt đầu xử lý ảnh: $uri")

        ocrService.recognizeFromUri(
            imageUri = uri,
            onSuccess = { text ->
                Log.d(TAG, "OCR xong — ${text}")
                if (text.isBlank()) {
                    Log.w(TAG, "Ảnh không chứa văn bản, dừng xử lý.")
                    return@recognizeFromUri
                }
                identifyThenTranslate(text)
            },
            onFailure = { e ->
                Log.e(TAG, "Lỗi bước OCR", e)
            }
        )
    }

    fun close() {
        ocrService.close()
        languageIdentifyService.close()
        translateService.close()
        Log.d(TAG, "ImageProcessor đã giải phóng tài nguyên.")
    }

    private fun identifyThenTranslate(text: String) {
        languageIdentifyService.identify(
            text = text,
            onSuccess = { languageTag ->
                Log.d(TAG, "Ngôn ngữ nhận dạng: $languageTag")

                translateService.translate(
                    text = text,
                    sourceLanguageTag = languageTag,
                    onSuccess = { translatedText ->
                        Log.i(TAG, "Dịch xong [$languageTag → en]:")
                        Log.i(TAG, "  Gốc  : $text")
                        Log.i(TAG, "  Dịch : $translatedText")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Lỗi bước Translate", e)
                    }
                )
            },
            onFailure = { e ->
                Log.e(TAG, "Lỗi bước LanguageIdentify", e)
            }
        )
    }
}
