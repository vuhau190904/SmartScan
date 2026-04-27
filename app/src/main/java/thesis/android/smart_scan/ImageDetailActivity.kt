package thesis.android.smart_scan

import android.net.Uri
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.HorizontalScrollView
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import thesis.android.smart_scan.R
import thesis.android.smart_scan.model.ImageCollection
import thesis.android.smart_scan.model.Reminder
import thesis.android.smart_scan.adapter.DetailImagePagerAdapter
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.receiver.ReminderReceiver
import thesis.android.smart_scan.service.mlkit.EntityResult
import thesis.android.smart_scan.service.mlkit.TextClassifierService
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionDelegate
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionUiHelper
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.EntityActionHelper
import thesis.android.smart_scan.util.inflateDialogTextInput
import android.text.method.LinkMovementMethod
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private lateinit var tvDescriptionLabel: TextView
    private lateinit var tvImageDescription: TextView
    private lateinit var etNote: EditText
    private lateinit var btnVoiceNote: ImageButton
    private lateinit var btnSaveNote: MaterialButton
    private lateinit var chipGroupImageCollections: ChipGroup
    private lateinit var btnAddImageToCollection: MaterialButton
    private lateinit var tvPageCounter: TextView
    private lateinit var tvEntitiesLabel: TextView
    private lateinit var entityCardsContainer: HorizontalScrollView
    private lateinit var entityCardsLinearLayout: LinearLayout
    private lateinit var tvScheduledRemindersLabel: TextView
    private lateinit var scheduledRemindersContent: LinearLayout
    private lateinit var scheduledRemindersAddChipGroup: ChipGroup
    private lateinit var scheduledRemindersChipGroup: ChipGroup
    private lateinit var tvNoReminders: TextView
    private var currentUri: Uri? = null
    private var currentImageId: Long = 0L
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4002) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, continue with pending reminder
                pendingReminderEntity?.let { entity ->
                    pendingReminderEntity = null
                    showReminderDialogWithEntity(entity)
                } ?: showReminderDialog()
            } else {
                Toast.makeText(this, R.string.reminder_permission_denied, Toast.LENGTH_SHORT).show()
                pendingReminderEntity = null
            }
        }
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
        tvDescriptionLabel = findViewById(R.id.tvDescriptionLabel)
        tvImageDescription = findViewById(R.id.tvDetailImageDescription)
        etNote = findViewById(R.id.etDetailNote)
        btnVoiceNote = findViewById(R.id.btnVoiceNote)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        chipGroupImageCollections = findViewById(R.id.chipGroupImageCollections)
        btnAddImageToCollection = findViewById(R.id.btnAddImageToCollection)
        tvPageCounter = findViewById(R.id.tvPageCounter)
        tvEntitiesLabel = findViewById(R.id.tvEntitiesLabel)
        entityCardsContainer = findViewById(R.id.entityCardsContainer)
        entityCardsLinearLayout = findViewById(R.id.entityCardsLinearLayout)
        tvScheduledRemindersLabel = findViewById(R.id.tvScheduledRemindersLabel)
        scheduledRemindersContent = findViewById(R.id.scheduledRemindersContent)
        scheduledRemindersAddChipGroup = findViewById(R.id.scheduledRemindersAddChipGroup)
        scheduledRemindersChipGroup = findViewById(R.id.scheduledRemindersChipGroup)
        tvNoReminders = findViewById(R.id.tvNoReminders)

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
        tvImageDescription.setTextIsSelectable(true)
    }

    private fun applyTextClassifierPolicyToDetailTextViews() {
        val tcm = getSystemService(TextClassificationManager::class.java) ?: return
        val systemClassifier = tcm.textClassifier
        tvImageDescription.setTextClassifier(systemClassifier)
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
        currentImageId = indexedImage?.id ?: 0L
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

        val imageDescription = indexedImage?.imageDescription?.takeIf { it.isNotBlank() }

        tvDescriptionLabel.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.visibility =
            if (imageDescription != null) View.VISIBLE else View.GONE
        tvImageDescription.text = imageDescription.orEmpty()

        etNote.setText(indexedImage?.note.orEmpty())
        renderCollectionChips(indexedImage?.id ?: 0L)

        val classifierEntities = indexedImage?.textClassifierJson
            ?.takeIf { it.isNotBlank() }
            ?.let { TextClassifierService.decodeEntityResults(it) }
            .orEmpty()
        applyTextClassifierPolicyToDetailTextViews()
        renderEntityCards(classifierEntities)
        renderScheduledReminders()
    }

    private fun renderScheduledReminders() {
        scheduledRemindersAddChipGroup.removeAllViews()
        scheduledRemindersChipGroup.removeAllViews()

        if (currentImageId == 0L) {
            tvScheduledRemindersLabel.visibility = View.GONE
            scheduledRemindersContent.visibility = View.GONE
            return
        }

        // Chip "+ Thêm" ở dòng riêng
        val addChip = Chip(this).apply {
            text = "+ Thêm"
            isClickable = true
            chipIcon = getDrawable(R.drawable.ic_alarm)
            setOnClickListener {
                showReminderDialog()
            }
        }
        scheduledRemindersAddChipGroup.addView(addChip)

        val reminders = ObjectBoxRepository.getPendingRemindersForImage(currentImageId)

        tvScheduledRemindersLabel.visibility = View.VISIBLE
        scheduledRemindersContent.visibility = View.VISIBLE

        if (reminders.isEmpty()) {
            tvNoReminders.visibility = View.VISIBLE
            return
        }

        tvNoReminders.visibility = View.GONE

        val dateFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
        reminders.forEach { reminder ->
            val chip = Chip(this).apply {
                text = "${reminder.title} • ${dateFormat.format(Date(reminder.reminderTime))}"
                isClickable = true
                isCheckable = false
                chipIcon = getDrawable(R.drawable.ic_alarm)
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    ReminderReceiver.cancelReminder(context, reminder.id)
                    ObjectBoxRepository.deleteReminder(reminder.id)
                    renderScheduledReminders()
                }
                setOnClickListener {
                    showReminderDialogForExistingReminder(reminder)
                }
            }

            scheduledRemindersChipGroup.addView(chip)
        }
    }

    private fun showReminderDialogForExistingReminder(reminder: Reminder) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etReminderTitle)
        val etDate = dialogView.findViewById<EditText>(R.id.etReminderDate)
        val etTime = dialogView.findViewById<EditText>(R.id.etReminderTime)
        val etNote = dialogView.findViewById<EditText>(R.id.etReminderNote)

        etTitle.setText(reminder.title)
        etNote.setText(reminder.note ?: "")

        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.reminderTime }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        etDate.setText(dateFormat.format(calendar.time))
        etTime.setText(timeFormat.format(calendar.time))

        var selectedDate = calendar.time
        var selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute = calendar.get(Calendar.MINUTE)

        etDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.reminder_date))
                .setSelection(calendar.timeInMillis)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = Date(selection)
                etDate.setText(dateFormat.format(selectedDate))
            }

            datePicker.show(supportFragmentManager, "datePicker")
        }

        etTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.reminder_time))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                etTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }

            timePicker.show(supportFragmentManager, "timePicker")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_reminder)
            .setView(dialogView)
            .setPositiveButton(R.string.reminder_set) { _, _ ->
                val title = etTitle.text?.toString()?.trim() ?: ""
                if (title.isEmpty()) {
                    Toast.makeText(this, R.string.reminder_empty_title, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val dateTime = Calendar.getInstance().apply {
                    time = selectedDate
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                }.time

                if (dateTime.time <= System.currentTimeMillis()) {
                    Toast.makeText(this, R.string.reminder_time_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                reminder.title = title
                reminder.reminderTime = dateTime.time
                reminder.note = etNote.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ObjectBoxRepository.putReminder(reminder)
                ReminderReceiver.cancelReminder(this, reminder.id)
                ReminderReceiver.scheduleReminder(this, reminder)
                renderScheduledReminders()
                Toast.makeText(this, R.string.reminder_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                ReminderReceiver.cancelReminder(this, reminder.id)
                ObjectBoxRepository.deleteReminder(reminder.id)
                renderScheduledReminders()
            }
            .show()
    }

    private fun renderEntityCards(entities: List<EntityResult>) {
        entityCardsLinearLayout.removeAllViews()

        if (entities.isEmpty()) {
            tvEntitiesLabel.visibility = View.GONE
            entityCardsContainer.visibility = View.GONE
            return
        }

        tvEntitiesLabel.visibility = View.VISIBLE
        entityCardsContainer.visibility = View.VISIBLE

        // Entity types that use the new reminder system
        val reminderEntityTypes = listOf("date", "dateTime", "datetime", "time")

        for (entity in entities.distinctBy { it.text }) {
            val cardView = layoutInflater.inflate(R.layout.item_entity_card, entityCardsLinearLayout, false)

            val tvEntityType = cardView.findViewById<TextView>(R.id.tvEntityType)
            val tvEntityValue = cardView.findViewById<TextView>(R.id.tvEntityValue)
            val chipGroupActions = cardView.findViewById<ChipGroup>(R.id.chipGroupActions)

            // Set entity type label and value
            val typeLabel = getEntityTypeLabel(entity.entityType)
            tvEntityType.text = typeLabel
            tvEntityValue.text = entity.text

            // For date/datetime/time entities: show calendar + alarm + reminder
            if (entity.entityType in reminderEntityTypes) {
                val actions = EntityActionHelper.buildActions(this, entity.entityType, entity.text)
                for (action in actions) {
                    val chip = Chip(this).apply {
                        text = action.label
                        isClickable = true
                        action.iconRes?.let { setChipIconResource(it) }
                        setOnClickListener {
                            if (action.copyText != null) {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Copied", action.copyText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@ImageDetailActivity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    startActivity(action.intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@ImageDetailActivity, R.string.action_no_app, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    chipGroupActions.addView(chip)
                }

                // Add reminder chip
                chipGroupActions.addView(createReminderChip(entity))
            } else {
                // For other entities: show existing actions (call, copy, etc.) + reminder
                val actions = EntityActionHelper.buildActions(this, entity.entityType, entity.text)
                for (action in actions) {
                    val chip = Chip(this).apply {
                        text = action.label
                        isClickable = true
                        setOnClickListener {
                            if (action.copyText != null) {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Copied", action.copyText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this@ImageDetailActivity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    startActivity(action.intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@ImageDetailActivity, R.string.action_no_app, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    chipGroupActions.addView(chip)
                }

                // Add reminder chip for all other entity types
                chipGroupActions.addView(createReminderChip(entity))
            }

            entityCardsLinearLayout.addView(cardView)
        }
    }

    private fun createReminderChip(entity: EntityResult): Chip {
        return Chip(this).apply {
            text = getString(R.string.action_reminder)
            isClickable = true
            chipIcon = getDrawable(R.drawable.ic_alarm)
            setOnClickListener {
                showReminderDialogWithEntity(entity)
            }
        }
    }

    private fun getEntityTypeLabel(entityType: String): String {
        return when (entityType) {
            "phone" -> "📞 Điện thoại"
            "address" -> "📍 Địa chỉ"
            "date" -> "📅 Ngày tháng"
            "datetime" -> "⏰ Thời gian"
            "email" -> "✉️ Email"
            "url" -> "🔗 Liên kết"
            "flightNumber" -> "✈️ Chuyến bay"
            else -> "🏷️ Khác"
        }
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

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var pendingReminderEntity: EntityResult? = null

    private fun showReminderDialog() {
        if (!hasNotificationPermission()) {
            pendingReminderEntity = null
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4002)
            Toast.makeText(this, R.string.reminder_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val indexedImage = currentUri?.let { ObjectBoxRepository.getByUri(it) } ?: return
        val defaultTitle = indexedImage.note?.takeIf { it.isNotBlank() } ?: ""

        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderTitle)
        val etDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderDate)
        val etTime = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderTime)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderNote)

        etTitle.setText(defaultTitle)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.reminder_date))
            .build()
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText(getString(R.string.reminder_time))
            .build()

        etDate.setOnClickListener {
            datePicker.show(supportFragmentManager, "DATE_PICKER")
            datePicker.addOnPositiveButtonClickListener {
                val selection = datePicker.selection
                if (selection != null) {
                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selection))
                    etDate.setText(dateStr)
                }
            }
        }

        etTime.setOnClickListener {
            timePicker.show(supportFragmentManager, "TIME_PICKER")
            timePicker.addOnPositiveButtonClickListener {
                val hour = timePicker.hour.toString().padStart(2, '0')
                val minute = timePicker.minute.toString().padStart(2, '0')
                etTime.setText("$hour:$minute")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_reminder)
            .setView(dialogView)
            .setPositiveButton(R.string.reminder_set) { _, _ ->
                val title = etTitle.text?.toString() ?: ""
                if (title.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_title, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dateStr = etDate.text?.toString() ?: ""
                val timeStr = etTime.text?.toString() ?: ""
                val note = etNote.text?.toString()?.takeIf { it.isNotBlank() }

                if (dateStr.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_date, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (timeStr.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_time, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createReminder(title, dateStr, timeStr, note)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReminderDialogWithEntity(entity: EntityResult) {
        if (!hasNotificationPermission()) {
            pendingReminderEntity = entity
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4002)
            Toast.makeText(this, R.string.reminder_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val indexedImage = currentUri?.let { ObjectBoxRepository.getByUri(it) } ?: return

        // Parse date/time from entity text
        val calendar = Calendar.getInstance()
        val parsedDate = EntityActionHelper.parseDateTime(entity.text)
            ?: EntityActionHelper.parseDate(entity.text)
        if (parsedDate != null) {
            calendar.timeInMillis = parsedDate
        } else {
            // For non-date entities, default to tomorrow at same time to ensure future
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val defaultTitle = indexedImage.note?.takeIf { it.isNotBlank() } ?: entity.text

        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderTitle)
        val etDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderDate)
        val etTime = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderTime)
        val etNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReminderNote)

        etTitle.setText(defaultTitle)

        // Pre-fill date
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(calendar.timeInMillis))
        etDate.setText(dateStr)

        // Pre-fill time - try to parse from entity, otherwise use current time (already +1 day if no date)
        val parsedTime = EntityActionHelper.parseTime(entity.text)
        val (hour, minute) = parsedTime
        etTime.setText("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.reminder_date))
            .setSelection(calendar.timeInMillis)
            .build()
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText(getString(R.string.reminder_time))
            .build()

        etDate.setOnClickListener {
            datePicker.show(supportFragmentManager, "DATE_PICKER")
            datePicker.addOnPositiveButtonClickListener {
                val selection = datePicker.selection
                if (selection != null) {
                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selection))
                    etDate.setText(dateStr)
                }
            }
        }

        etTime.setOnClickListener {
            timePicker.show(supportFragmentManager, "TIME_PICKER")
            timePicker.addOnPositiveButtonClickListener {
                val hour = timePicker.hour.toString().padStart(2, '0')
                val minute = timePicker.minute.toString().padStart(2, '0')
                etTime.setText("$hour:$minute")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_reminder)
            .setView(dialogView)
            .setPositiveButton(R.string.reminder_set) { _, _ ->
                val title = etTitle.text?.toString() ?: ""
                if (title.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_title, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dateStr = etDate.text?.toString() ?: ""
                val timeStr = etTime.text?.toString() ?: ""
                val note = etNote.text?.toString()?.takeIf { it.isNotBlank() }

                if (dateStr.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_date, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (timeStr.isBlank()) {
                    Toast.makeText(this, R.string.reminder_empty_time, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createReminder(title, dateStr, timeStr, note)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createReminder(title: String, dateStr: String, timeStr: String, note: String?) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateTimeStr = "$dateStr $timeStr"
        val dateTime = dateFormat.parse(dateTimeStr)

        if (dateTime == null || dateTime.time <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.reminder_time_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        val image = currentUri?.let { ObjectBoxRepository.getByUri(it) } ?: return

        val reminder = Reminder(
            imageId = image.id,
            title = title,
            note = note,
            reminderTime = dateTime.time,
            createdAt = System.currentTimeMillis()
        )
        val reminderId = ObjectBoxRepository.putReminder(reminder)

        Log.d("Reminder", reminder.toString())

        ReminderReceiver.scheduleReminder(applicationContext, reminder.copy(id = reminderId))

        Toast.makeText(this, R.string.reminder_created, Toast.LENGTH_SHORT).show()
        renderScheduledReminders()
    }
}
