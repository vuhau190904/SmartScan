package thesis.android.smart_scan

import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import thesis.android.smart_scan.adapter.DetailImagePagerAdapter
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.util.Constant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ImageDetailActivity : AppCompatActivity() {

    companion object {
        private const val PRELOAD_RADIUS = 2
        private const val FLING_MIN_DISTANCE = 80
        private const val FLING_MIN_VELOCITY = 200
        private const val TOP_BAR_EXTRA_PADDING_DP = 8
    }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var gestureDetector: GestureDetector

    private lateinit var tvFileName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvDimensions: TextView
    private lateinit var tvOcrLabel: TextView
    private lateinit var tvOcrText: TextView
    private lateinit var tvDescriptionLabel: TextView
    private lateinit var tvImageDescription: TextView
    private lateinit var tvNoteLabel: TextView
    private lateinit var etNote: EditText
    private lateinit var btnSaveNote: com.google.android.material.button.MaterialButton
    private lateinit var tvPageCounter: TextView
    private var currentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_image_detail)

        val uris = resolveUris() ?: run { finish(); return }
        val startPosition = intent.getIntExtra(Constant.IMAGE_POSITION, 0)
            .coerceIn(0, uris.lastIndex)

        preloadAround(uris, startPosition)

        bindViews()
        setupViewPager(uris, startPosition)
        setupBottomSheet()
        setupSwipeUpGesture()
        setupWindowInsets()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun resolveUris(): List<Uri>? {
        val strings = intent.getStringArrayListExtra(Constant.IMAGE_URIS)
            ?: intent.getStringExtra(Constant.IMAGE_URI)?.let { arrayListOf(it) }
            ?: return null
        return strings.map { it.toUri() }
    }

    private fun bindViews() {
        tvFileName = findViewById(R.id.tvDetailFileName)
        tvDate = findViewById(R.id.tvDetailDate)
        tvSize = findViewById(R.id.tvDetailSize)
        tvDimensions = findViewById(R.id.tvDetailDimensions)
        tvOcrLabel = findViewById(R.id.tvOcrLabel)
        tvOcrText = findViewById(R.id.tvDetailOcrText)
        tvDescriptionLabel = findViewById(R.id.tvDescriptionLabel)
        tvImageDescription = findViewById(R.id.tvDetailImageDescription)
        tvNoteLabel = findViewById(R.id.tvNoteLabel)
        etNote = findViewById(R.id.etDetailNote)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        tvPageCounter = findViewById(R.id.tvPageCounter)

        btnSaveNote.setOnClickListener {
            val uri = currentUri ?: return@setOnClickListener
            ObjectBoxRepository.updateNoteByUri(uri, etNote.text?.toString()?.trim())
            Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewPager(uris: List<Uri>, startPosition: Int) {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = DetailImagePagerAdapter(uris)
        viewPager.offscreenPageLimit = 2
        viewPager.setCurrentItem(startPosition, false)

        updatePageCounter(startPosition + 1, uris.size)
        updateMetadata(uris[startPosition])

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
                updateMetadata(uris[position])
                updatePageCounter(position + 1, uris.size)
            }
        })
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(
            findViewById<LinearLayout>(R.id.bottomSheet)
        ).apply {
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setupSwipeUpGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                val dy = e2.y - (e1?.y ?: return false)
                val isUpward = dy < -FLING_MIN_DISTANCE
                    && velocityY < -FLING_MIN_VELOCITY
                    && abs(velocityY) > abs(velocityX)

                if (isUpward && bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    return true
                }
                return false
            }
        })
    }

    private fun setupWindowInsets() {
        val topBar = findViewById<FrameLayout>(R.id.topBar)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraTop = (TOP_BAR_EXTRA_PADDING_DP * resources.displayMetrics.density).toInt()
            topBar.setPadding(topBar.paddingLeft, bars.top + extraTop, topBar.paddingRight, topBar.paddingBottom)
            bottomSheet.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun updatePageCounter(current: Int, total: Int) {
        tvPageCounter.text = getString(R.string.page_counter, current, total)
    }

    private fun updateMetadata(uri: Uri) {
        currentUri = uri
        val indexedImage = ObjectBoxRepository.getByUri(uri)
        val info = MediaContentRepository.getImageDetails(uri)
        if (info != null) {
            tvFileName.text = info.displayName
            tvDate.text = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
                .format(Date(info.dateAdded * 1000L))
            tvSize.text = formatFileSize(info.size)
            tvDimensions.text = if (info.width > 0 && info.height > 0) {
                "${info.width} × ${info.height} px"
            } else {
                getString(R.string.unknown)
            }
        } else {
            tvFileName.text = uri.lastPathSegment ?: getString(R.string.unknown)
            val unknown = getString(R.string.unknown)
            tvDate.text = unknown
            tvSize.text = unknown
            tvDimensions.text = unknown
        }

        val ocrText = indexedImage?.ocrText?.takeIf { it.isNotBlank() }
        val imageDescription = indexedImage?.imageDescription?.takeIf { it.isNotBlank() }

        tvOcrLabel.visibility = if (ocrText != null) View.VISIBLE else View.GONE
        tvOcrText.visibility = if (ocrText != null) View.VISIBLE else View.GONE
        tvOcrText.text = ocrText.orEmpty()

        tvDescriptionLabel.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.text = imageDescription.orEmpty()

        etNote.setText(indexedImage?.note.orEmpty())
    }

    private fun preloadAround(uris: List<Uri>, center: Int) {
        val range = (center - PRELOAD_RADIUS).coerceAtLeast(0)..
            (center + PRELOAD_RADIUS).coerceAtMost(uris.lastIndex)
        for (i in range) Glide.with(this).load(uris[i]).preload()
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
