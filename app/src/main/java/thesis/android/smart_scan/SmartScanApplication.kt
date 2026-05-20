package thesis.android.smart_scan

import android.app.Application
import thesis.android.smart_scan.util.PerformanceLogger

class SmartScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PerformanceLogger.markProcessStart()
    }
}
