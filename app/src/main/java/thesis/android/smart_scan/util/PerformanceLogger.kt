package thesis.android.smart_scan.util

import android.util.Log

object PerformanceLogger {
    const val TAG_STARTUP = "StartupTime"
    const val TAG_RESPONSE_LATENCY = "ResponseLatency"
    const val TAG_PROCESSING_TIME = "ProcessingTime"

    var processStartedAtMs: Long = System.currentTimeMillis()
        private set

    fun markProcessStart() {
        processStartedAtMs = now()
        Log.i(TAG_STARTUP, "process_start start_ms=$processStartedAtMs")
    }

    fun now(): Long = System.currentTimeMillis()

    fun logDuration(tag: String, metric: String, startedAtMs: Long, details: String = "") {
        if (startedAtMs <= 0L) return

        val endedAtMs = now()
        val suffix = details.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        Log.i(
            tag,
            "$metric duration_ms=${endedAtMs - startedAtMs} start_ms=$startedAtMs end_ms=$endedAtMs$suffix"
        )
    }

    fun <T> measure(tag: String, metric: String, details: String = "", block: () -> T): T {
        val startedAtMs = now()
        return try {
            block()
        } finally {
            logDuration(tag, metric, startedAtMs, details)
        }
    }

    suspend fun <T> measureSuspend(
        tag: String,
        metric: String,
        details: String = "",
        block: suspend () -> T
    ): T {
        val startedAtMs = now()
        return try {
            block()
        } finally {
            logDuration(tag, metric, startedAtMs, details)
        }
    }
}
