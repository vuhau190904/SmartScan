package thesis.android.smart_scan

import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import thesis.android.smart_scan.model.ImageCollection
import thesis.android.smart_scan.adapter.DetailImagePagerAdapter
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.TextClassifierService
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionDelegate
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionUiHelper
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.inflateDialogTextInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
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
    private lateinit var etNote: EditText
    private lateinit var btnVoiceNote: ImageButton
    private lateinit var btnSaveNote: MaterialButton
    private lateinit var chipGroupImageCollections: ChipGroup
    private lateinit var btnAddImageToCollection: MaterialButton
    private lateinit var tvPageCounter: TextView
    private var currentUri: Uri? = null
    private var isVoiceTypingNote = false

    private lateinit var speechRecognitionDelegate: SpeechRecognitionDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechRecognitionDelegate = SpeechRecognitionDelegate(this)
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
        etNote = findViewById(R.id.etDetailNote)
        btnVoiceNote = findViewById(R.id.btnVoiceNote)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        chipGroupImageCollections = findViewById(R.id.chipGroupImageCollections)
        btnAddImageToCollection = findViewById(R.id.btnAddImageToCollection)
        tvPageCounter = findViewById(R.id.tvPageCounter)

        btnSaveNote.setOnClickListener {
            val uri = currentUri ?: return@setOnClickListener
            ObjectBoxRepository.updateNoteByUri(uri, etNote.text?.toString()?.trim())
            Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
        }

        btnVoiceNote.setOnClickListener {
            startVoiceNoteInput()
        }

        btnAddImageToCollection.setOnClickListener {
            val image = currentUri?.let { uri -> ObjectBoxRepository.getByUri(uri) } ?: return@setOnClickListener
            showAddToCollectionDialog(image.id)
        }

        setupSelectableTextWithSmartActions()
    }

    private fun setupSelectableTextWithSmartActions() {
        listOf(tvOcrText, tvImageDescription).forEach { tv ->
            tv.setTextIsSelectable(true)
        }
        applyTextClassifierPolicyToDetailTextViews()
    }

    private fun applyTextClassifierPolicyToDetailTextViews() {
        val tcm = getSystemService(TextClassificationManager::class.java) ?: return
        val systemClassifier = tcm.textClassifier
        tvOcrText.setTextClassifier(systemClassifier)
        tvImageDescription.setTextClassifier(TextClassifier.NO_OP)
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
        val classifierEntities = indexedImage?.textClassifierJson
            ?.takeIf { it.isNotBlank() }
            ?.let { TextClassifierService.decodeEntityResults(it) }
            .orEmpty()
        val ocrDisplay: CharSequence = when {
            ocrText == null -> ""
            classifierEntities.isNotEmpty() ->
                TextClassifierService.buildHighlightedEntitySpannable(this, ocrText, classifierEntities)
            else -> ocrText
        }
        val imageDescription = indexedImage?.imageDescription?.takeIf { it.isNotBlank() }

        tvOcrLabel.visibility = if (ocrText != null) View.VISIBLE else View.GONE
        tvOcrText.visibility = if (ocrText != null) View.VISIBLE else View.GONE
        tvOcrText.text = ocrDisplay

        tvDescriptionLabel.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.text = imageDescription.orEmpty()

        etNote.setText(indexedImage?.note.orEmpty())
        renderCollectionChips(indexedImage?.id ?: 0L)

        applyTextClassifierPolicyToDetailTextViews()
    }

    private fun renderCollectionChips(imageId: Long) {
        chipGroupImageCollections.removeAllViews()
        if (imageId == 0L) return
        val collections = ObjectBoxRepository.getCollectionsForImage(imageId)
        collections.forEach { collection ->
            val chip = Chip(this).apply {
                text = collection.name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    ObjectBoxRepository.removeImageFromCollection(imageId, collection.id)
                    renderCollectionChips(imageId)
                    setResult(RESULT_OK)
                }
            }
            chipGroupImageCollections.addView(chip)
        }
    }

    private fun showAddToCollectionDialog(imageId: Long) {
        val existingCollectionIds = ObjectBoxRepository.getCollectionsForImage(imageId).map { it.id }.toSet()
        val availableCollections = ObjectBoxRepository.getAllCollections()
            .filterNot { it.id in existingCollectionIds }
        val options = buildList {
            add(getString(R.string.create_collection_with_plus))
            addAll(availableCollections.map(ImageCollection::name))
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_to_collection))
            .setItems(options) { _, which ->
                if (which == 0) {
                    showCreateCollectionAndAddImageDialog(imageId)
                    return@setItems
                }
                val selected = availableCollections.getOrNull(which - 1) ?: return@setItems
                addImageToCollectionAndRefresh(imageId, selected.id)
            }
            .show()
    }

    private fun showCreateCollectionAndAddImageDialog(imageId: Long) {
        val (inputView, input) = inflateDialogTextInput(getString(R.string.collection_name_hint))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.create_collection))
            .setView(inputView)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val created = ObjectBoxRepository.createCollection(input.text?.toString().orEmpty())
                if (created == null) {
                    Toast.makeText(this, getString(R.string.invalid_collection_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                addImageToCollectionAndRefresh(imageId, created.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun addImageToCollectionAndRefresh(imageId: Long, collectionId: Long) {
        val added = ObjectBoxRepository.addImageToCollection(imageId, collectionId)
        if (!added) {
            Toast.makeText(this, getString(R.string.image_already_in_collection), Toast.LENGTH_SHORT).show()
            return
        }
        renderCollectionChips(imageId)
        setResult(RESULT_OK)
        Toast.makeText(this, getString(R.string.image_added_to_collection), Toast.LENGTH_SHORT).show()
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

    private fun startVoiceNoteInput() {
        if (isVoiceTypingNote) return
        if (!hasRecordAudioPermission()) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 4001)
            Toast.makeText(this, getString(R.string.voice_permission_required), Toast.LENGTH_SHORT).show()
            return
        }

        isVoiceTypingNote = true
        btnVoiceNote.isEnabled = false
        Toast.makeText(this, getString(R.string.voice_listening), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                SpeechRecognitionUiHelper.recognizeAndHandle(
                    context = this@ImageDetailActivity,
                    delegate = speechRecognitionDelegate
                ) { text ->
                    val current = etNote.text?.toString().orEmpty().trim()
                    val merged = if (current.isBlank()) text else "$current $text"
                    etNote.setText(merged)
                    etNote.setSelection(merged.length)
                }
            } finally {
                onVoiceNoteFinished()
            }
        }
    }

    private fun onVoiceNoteFinished() {
        isVoiceTypingNote = false
        btnVoiceNote.isEnabled = true
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
