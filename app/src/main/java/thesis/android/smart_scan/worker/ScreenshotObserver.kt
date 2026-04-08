package thesis.android.smart_scan.worker

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import thesis.android.smart_scan.util.Constant

class ScreenshotObserver(private val context: Context) : ContentObserver(handler) {
    companion object {
        private const val TAG = "ScreenshotObserver"
        private val handler: Handler by lazy {
            val thread = HandlerThread("ScreenshotWatcher").apply { start() }
            Handler(thread.looper)
        }
    }

    private val contentResolver = context.contentResolver
    private val externalImagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun start() {
        contentResolver.registerContentObserver(externalImagesUri, true, this)
        Log.d(TAG, "Observer đã đăng ký — đang lắng nghe ảnh mới...")
    }

    fun stop() {
        contentResolver.unregisterContentObserver(this)
        Log.d(TAG, "Observer đã hủy đăng ký.")
    }

    override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
        super.onChange(selfChange, uri)
        if (uri != null && flags == 5) {
            Handler(Looper.getMainLooper()).postDelayed({
                scheduleScanWorker(uri)
            }, 1000)
        }
    }

    private fun scheduleScanWorker(uri: Uri) {
        val data = workDataOf(Constant.IMAGE_URI to uri.toString())

        val scanRequest = OneTimeWorkRequestBuilder<BackgroundProcessingWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(scanRequest)
        Log.d(TAG, "Đã gửi yêu cầu xử lý ảnh $uri sang BackgroundProcessingWorker")
    }
}
