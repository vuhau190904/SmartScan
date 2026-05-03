package thesis.android.smart_scan.config

import java.util.Locale

data class AppConfig(
    val userLanguage: String = Locale.getDefault().language,
    val objectDetectorScoreThreshold: Float = 0.8f,
)
