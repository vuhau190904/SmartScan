package thesis.android.smart_scan.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import thesis.android.smart_scan.ImageDetailActivity
import thesis.android.smart_scan.R
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.util.Constant

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        private const val TAG = "ReminderWorker"
        const val REMINDER_CHANNEL_ID = "smartscan_reminder_channel"
        const val REMINDER_CHANNEL_NAME = "Nhắc nhở"
        const val NOTIFICATION_ID_BASE = 2000
    }

    override suspend fun doWork(): Result {
        Log.w(TAG, "work")
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1)
        if (reminderId == -1L) {
            Log.w(TAG, "No reminderId in inputData")
            return Result.failure()
        }

        val reminder = ObjectBoxRepository.getReminder(reminderId)
        if (reminder == null) {
            Log.d(TAG, "Reminder id=$reminderId not found — already deleted or expired")
            return Result.success()
        }

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
                ObjectBoxRepository.deleteReminder(reminderId)
                return Result.success()
            }
        }

        showNotification(reminder.id, reminder.title, reminder.note, reminder.imageId)

        ObjectBoxRepository.deleteReminder(reminderId)
        Log.d(TAG, "Reminder id=$reminderId fired and deleted from DB")

        return Result.success()
    }

    private fun showNotification(reminderId: Long, title: String, note: String?, imageId: Long) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nhắc nhở từ SmartScan"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val imageUri = ObjectBoxRepository.getById(imageId)?.uri?.toString() ?: ""

        val intent = Intent(applicationContext, ImageDetailActivity::class.java).apply {
            putExtra(Constant.IMAGE_URI, imageUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = note?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.reminder_tap_to_view)

        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(NOTIFICATION_ID_BASE + reminderId.toInt(), notification)
    }
}
