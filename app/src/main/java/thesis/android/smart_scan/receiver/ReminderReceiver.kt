package thesis.android.smart_scan.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import thesis.android.smart_scan.model.Reminder
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.worker.ReminderWorker

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_REMINDER_ALARM = "thesis.android.smart_scan.ACTION_REMINDER_ALARM"
        const val EXTRA_REMINDER_ID = "reminder_id"

        fun scheduleReminder(context: Context, reminder: Reminder) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(ACTION_REMINDER_ALARM, null, context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Check if we can schedule exact alarms on Android 12+
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            Log.d(TAG, "Scheduling reminder id=${reminder.id} at=${reminder.reminderTime}, canScheduleExact=$canScheduleExact")

            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled reminder id=${reminder.id}")
        }

        fun cancelReminder(context: Context, reminderId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                this.action = ACTION_REMINDER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive received, action=${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "BOOT_COMPLETED — rescheduling pending reminders")
                ObjectBoxRepository.init(context)
                rescheduleAllPendingReminders(context)
            }
            ACTION_REMINDER_ALARM -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
                if (reminderId == -1L) {
                    Log.w(TAG, "Alarm received but no reminderId")
                    return
                }
                Log.d(TAG, "Alarm fired for reminder id=$reminderId")
                enqueueReminderWorker(context, reminderId)
            }
            else -> {
                Log.d(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    private fun enqueueReminderWorker(context: Context, reminderId: Long) {
        val inputData = Data.Builder()
            .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(inputData)
            .addTag("reminder_$reminderId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "WorkManager enqueued reminder id=$reminderId")
    }

    private fun rescheduleAllPendingReminders(context: Context) {
        val pending = ObjectBoxRepository.getPendingReminders()
        pending.forEach { reminder ->
            scheduleReminder(context, reminder)
        }
        Log.d(TAG, "Rescheduled ${pending.size} pending reminders")
    }
}