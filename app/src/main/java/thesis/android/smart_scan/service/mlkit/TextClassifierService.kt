package thesis.android.smart_scan.service.mlkit

import android.content.Context
import android.text.style.ClickableSpan
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.util.Log
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextClassifier.EntityConfig
import android.view.textclassifier.TextLinks
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import thesis.android.smart_scan.R
import java.lang.reflect.Modifier

data class EntityResult(
    val entityType: String,
    val text: String,
    val start: Int = -1,
    val end: Int = -1,
)

object TextClassifierService {

    private const val TAG = "TextClassifierService"

    private lateinit var textClassifier: TextClassifier

    private val supportedEntityTypesInternal: List<String> by lazy {
        reflectSdkEntityTypeStrings().ifEmpty { FALLBACK_ENTITY_TYPES }
    }

    fun init(context: Context) {
        textClassifier = context.applicationContext
            .getSystemService(TextClassificationManager::class.java).textClassifier
        Log.d(TAG, "TextClassifierService khởi tạo — ${supportedEntityTypesInternal.size} loại entity (SDK).")
    }

    fun encodeEntityResults(results: List<EntityResult>): String {
        val arr = JSONArray()
        for (e in results) {
            arr.put(
                JSONObject().apply {
                    put("entityType", e.entityType)
                    put("text", e.text)
                    put("start", e.start)
                    put("end", e.end)
                }
            )
        }
        return arr.toString()
    }

    fun decodeEntityResults(json: String): List<EntityResult> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        EntityResult(
                            entityType = o.getString("entityType"),
                            text = o.getString("text"),
                            start = if (o.has("start")) o.getInt("start") else -1,
                            end = if (o.has("end")) o.getInt("end") else -1,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "decodeEntityResults lỗi", e)
            emptyList()
        }
    }

    fun buildHighlightedEntitySpannable(
        context: Context,
        fullText: String,
        entities: List<EntityResult>,
        onEntityClick: (EntityResult) -> Unit
    ): CharSequence {
        if (fullText.isEmpty() || entities.isEmpty()) return fullText
        val highlightColor = ContextCompat.getColor(context, R.color.color_primary_container)
        val linkColor = ContextCompat.getColor(context, R.color.color_primary)
        val ss = SpannableString(fullText)
        val grouped = entities.groupBy { it.start to it.end }
        for ((range, list) in grouped.entries.sortedWith(
            compareBy({ it.key.first }, { it.key.second }),
        )) {
            val types = list.map { it.entityType }.toSet()
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onEntityClick(list.first())
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = types.any {
                        it == TextClassifier.TYPE_PHONE || it == TextClassifier.TYPE_URL ||
                        it == TextClassifier.TYPE_EMAIL || it == TextClassifier.TYPE_ADDRESS ||
                        it == TextClassifier.TYPE_DATE || it == TextClassifier.TYPE_DATE_TIME ||
                        it == TextClassifier.TYPE_FLIGHT_NUMBER
                    }
                    ds.color = linkColor
                }
            }
            ss.setSpan(clickableSpan, range.first, range.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            applyEntitySpans(ss, types, range.first, range.second, highlightColor, linkColor)
        }
        return ss
    }

    private fun applyEntitySpans(
        ss: SpannableString,
        types: Set<String>,
        start: Int,
        end: Int,
        highlightColor: Int,
        linkColor: Int,
    ) {
        val spanFlag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val hasAddress = types.any { it == TextClassifier.TYPE_ADDRESS }
        val hasDate = types.any {
            it == TextClassifier.TYPE_DATE || it == TextClassifier.TYPE_DATE_TIME
        }
        val hasLinkLike = types.any {
            it == TextClassifier.TYPE_URL ||
                it == TextClassifier.TYPE_EMAIL ||
                it == TextClassifier.TYPE_PHONE
        }
        val hasFlight = types.any { it == TextClassifier.TYPE_FLIGHT_NUMBER }
        val underline = hasAddress || hasDate || hasLinkLike || hasFlight ||
            types.any { it != TextClassifier.TYPE_OTHER }

        if (hasAddress) {
            ss.setSpan(BackgroundColorSpan(highlightColor), start, end, spanFlag)
        }
        if (hasLinkLike) {
            ss.setSpan(ForegroundColorSpan(linkColor), start, end, spanFlag)
        }
        if (underline) {
            ss.setSpan(UnderlineSpan(), start, end, spanFlag)
        }
    }

    fun extractMetadata(text: String): List<EntityResult> {
        if (text.isEmpty()) return emptyList()

        val entityConfig = EntityConfig.Builder()
            .setIncludedTypes(supportedEntityTypesInternal)
            .includeTypesFromTextClassifier(true)
            .build()

        return try {
            val request = TextLinks.Request.Builder(text)
                .setEntityConfig(entityConfig)
                .build()
            val textLinks = textClassifier.generateLinks(request)
            buildList {
                for (link in textLinks.links) {
                    val spanText = text.substring(link.start, link.end)
                    for (i in 0 until link.entityCount) {
                        add(
                            EntityResult(
                                entityType = link.getEntity(i),
                                text = spanText,
                                start = link.start,
                                end = link.end,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractMetadata thất bại (length=${text.length})", e)
            emptyList()
        }
    }
}

private fun reflectSdkEntityTypeStrings(): List<String> =
    TextClassifier::class.java.declaredFields.asSequence()
        .filter { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java &&
                field.name.startsWith("TYPE_")
        }
        .mapNotNull { field ->
            runCatching {
                (field.get(null) as? String)?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
        .distinct()
        .sorted()
        .toList()

private val FALLBACK_ENTITY_TYPES = listOf(
    TextClassifier.TYPE_OTHER,
    TextClassifier.TYPE_EMAIL,
    TextClassifier.TYPE_PHONE,
    TextClassifier.TYPE_ADDRESS,
    TextClassifier.TYPE_URL,
    TextClassifier.TYPE_DATE,
    TextClassifier.TYPE_DATE_TIME,
    TextClassifier.TYPE_FLIGHT_NUMBER,
)
