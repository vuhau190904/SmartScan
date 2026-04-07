package thesis.android.smart_scan.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import thesis.android.smart_scan.MainActivity
import thesis.android.smart_scan.R
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.worker.ScreenshotObserver

class ScreenshotForegroundService : Service() {

    companion object {
        private const val TAG = "ScreenshotForegroundService"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, ScreenshotForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, ScreenshotForegroundService::class.java)
            )
        }
    }

    private lateinit var screenshotObserver: ScreenshotObserver

    override fun onCreate() {
        super.onCreate()
        screenshotObserver = ScreenshotObserver(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service bắt đầu.")
        startForeground(Constant.NOTIFICATION_ID, buildNotification())
        screenshotObserver.start()
        // START_STICKY: Android tự khởi động lại service nếu bị kill do thiếu RAM
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotObserver.stop()
        Log.d(TAG, "Service dừng.")
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun buildNotification() = run {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                Constant.NOTIFICATION_CHANNEL_ID,
                Constant.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(this, Constant.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SmartScan đang hoạt động")
            .setContentText("Đang theo dõi ảnh mới trong nền...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
