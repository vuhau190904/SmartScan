package thesis.android.smart_scan.config

import java.util.Locale

data class AppConfig(
    val userLanguage: String = Locale.getDefault().language
)
