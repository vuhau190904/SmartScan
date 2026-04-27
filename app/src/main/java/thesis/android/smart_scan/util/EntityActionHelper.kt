package thesis.android.smart_scan.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import thesis.android.smart_scan.R

object EntityActionHelper {

    data class EntityAction(
        val label: String,
        val intent: Intent? = null,
        val copyText: String? = null,
        val iconRes: Int? = null
    )

    fun buildActions(context: Context, entityType: String, entityText: String): List<EntityAction> {
        return when (entityType) {
            "phone" -> listOfNotNull(
                EntityAction(
                    context.getString(R.string.action_call),
                    Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${entityText.trim()}")
                    },
                    iconRes = R.drawable.ic_call
                ),
                EntityAction(
                    label = context.getString(R.string.action_copy),
                    copyText = entityText,
                    iconRes = R.drawable.ic_copy
                )
            )

            "address" -> listOf(
                EntityAction(
                    context.getString(R.string.action_open_maps),
                    intent = openMapsIntent(entityText),
                    iconRes = R.drawable.ic_location
                )
            )

            "date", "dateTime", "datetime" -> {
                val actions = mutableListOf<EntityAction>()
                // Try to parse as date/datetime first
                val parsedDate = parseDateTime(entityText) ?: parseDate(entityText)
                if (parsedDate != null) {
                    // Has actual date component - add calendar
                    actions.add(EntityAction(
                        context.getString(R.string.action_add_to_calendar),
                        Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsedDate)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parsedDate + 3600000)
                            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, !entityText.contains(Regex("\\d{1,2}:\\d{2}")))
                        },
                        iconRes = R.drawable.ic_calendar
                    ))
                }
                // For dateTime, or if text looks like a time, add alarm
                val hasTimeOnly = Regex("\\d{1,2}:\\d{2}").containsMatchIn(entityText) ||
                                  Regex("\\d{1,2}h\\d{2}", RegexOption.IGNORE_CASE).containsMatchIn(entityText)
                if (hasTimeOnly) {
                    val alarmIntent = createAlarmIntent(context, entityText, isDateTime = parsedDate != null)
                    alarmIntent?.let { actions.add(it) }
                }
                // Fallback: if no actions but text looks like time, add alarm anyway
                if (actions.isEmpty() && hasTimeOnly) {
                    val alarmIntent = createAlarmIntent(context, entityText, isDateTime = false)
                    alarmIntent?.let { actions.add(it) }
                }
                actions
            }

            "time" -> {
                val alarmIntent = createAlarmIntent(context, entityText, isDateTime = false)
                listOfNotNull(alarmIntent)
            }

            "email" -> listOf(
                EntityAction(
                    context.getString(R.string.action_send_email),
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${entityText.trim()}")
                    },
                    iconRes = R.drawable.ic_email
                )
            )

            "url" -> listOf(
                EntityAction(
                    context.getString(R.string.action_open_link),
                    intent = openUrlIntent(entityText),
                    iconRes = R.drawable.ic_link
                ),
                EntityAction(
                    label = context.getString(R.string.action_copy),
                    copyText = entityText,
                    iconRes = R.drawable.ic_copy
                )
            )

            else -> emptyList()
        }
    }

    private fun createAlarmIntent(context: Context, timeText: String, isDateTime: Boolean): EntityAction? {
        val (hour, minute) = parseTime(timeText)

        // Create SET_ALARM intent with pre-filled data
        // User will see the alarm UI and can confirm before saving
        val setAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show UI for user to confirm
            if (isDateTime) {
                putExtra(AlarmClock.EXTRA_MESSAGE, timeText)
            }
        }

        // Try SET_ALARM first - most direct way to set an alarm
        // Just return the intent - caller will start it
        return try {
            EntityAction(context.getString(R.string.action_set_reminder), setAlarmIntent, iconRes = R.drawable.ic_alarm)
        } catch (e: Exception) {
            // Fallback: try SHOW_ALARMS - opens the alarm app to alarms tab
            try {
                val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                EntityAction(context.getString(R.string.action_set_reminder), showAlarmsIntent, iconRes = R.drawable.ic_alarm)
            } catch (e2: Exception) {
                // Final fallback: try to open clock app via component name
                try {
                    val clockIntent = Intent().apply {
                        setClassName("com.android.deskclock", "com.android.deskclock.AlarmClock")
                    }
                    EntityAction(context.getString(R.string.action_set_reminder), clockIntent, iconRes = R.drawable.ic_alarm)
                } catch (e3: Exception) {
                    null // No alarm app available
                }
            }
        }
    }

    internal fun parseTime(timeText: String): Pair<Int, Int> {
        // Try common time formats: "14:30", "2:30 PM", "14h30", "2h30"
        val patterns = listOf(
            Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d{1,2})h(\\d{2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d{1,2}) giờ (\\d{2})?", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(timeText)
            if (matcher.find()) {
                var hour = matcher.group(1)?.toIntOrNull() ?: return Pair(12, 0)
                val minute = matcher.group(2)?.toIntOrNull() ?: 0

                // Handle AM/PM
                if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                    val ampm = matcher.group(3)?.uppercase()
                    if (ampm == "PM" && hour < 12) hour += 12
                    if (ampm == "AM" && hour == 12) hour = 0
                }

                return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            }
        }

        // Default to current time if parsing fails
        val calendar = Calendar.getInstance()
        return Pair(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    internal fun parseDateTime(dateTimeText: String): Long? {
        val datePatterns = listOf(
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("d/M/yyyy H:mm", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("d/M/yyyy", Locale.getDefault())
        )

        for (format in datePatterns) {
            try {
                format.isLenient = false
                val date = format.parse(dateTimeText.trim())
                if (date != null && date.time > System.currentTimeMillis() - 86400000) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    internal fun parseDate(dateText: String): Long? {
        val datePatterns = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("d/M/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy", Locale(Locale.getDefault().language)),
            SimpleDateFormat("d MMM yyyy", Locale(Locale.getDefault().language))
        )

        for (format in datePatterns) {
            try {
                format.isLenient = false
                val date = format.parse(dateText.trim())
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    private fun openMapsIntent(address: String): Intent {
        val encoded = URLEncoder.encode(address, "UTF-8")
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
        }
    }

    private fun openUrlIntent(url: String): Intent {
        val cleanUrl = if (url.startsWith("http")) url else "https://$url"
        return Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
    }
}