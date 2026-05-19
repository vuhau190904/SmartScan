package thesis.android.smart_scan

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import thesis.android.smart_scan.adapter.CollectionDetailImageAdapter
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.processor.SearchProcessor
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionDelegate
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionUiHelper
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.inflateDialogTextInput

class CollectionDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CollectionDetailActivity"
        private const val SEARCH_DEBOUNCE_MS = 1000L
    }

    private lateinit var tvCollectionTitle: TextView
    private lateinit var etCollectionSearch: EditText
    private lateinit var btnVoiceCollectionSearch: ImageButton
    private lateinit var searchContainer: View
    private lateinit var rvCollectionDetailImages: RecyclerView
    private lateinit var tvDetailEmpty: TextView
    private lateinit var btnEditCollection: ImageButton
    private lateinit var btnDeleteCollection: ImageButton
    private lateinit var btnRemoveFromCollection: MaterialButton
    private lateinit var fabAddToCollection: FloatingActionButton

    private lateinit var adapter: CollectionDetailImageAdapter

    private var collectionId: Long = 0L
    private var collectionName: String = ""
    private var currentItems: List<Image> = emptyList()
    private var isSelectionMode = false
    private var currentQuery = ""
    private var isVoiceSearching = false
    private var searchJob: Job? = null
    private var loadImagesJob: Job? = null

    private lateinit var speechRecognitionDelegate: SpeechRecognitionDelegate

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            refreshCollectionImages()
        }
    }

    private val imageDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            refreshCollectionImages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechRecognitionDelegate = SpeechRecognitionDelegate(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_collection_detail)

        collectionId = intent.getLongExtra(Constant.COLLECTION_ID, 0L)
        collectionName = intent.getStringExtra(Constant.COLLECTION_NAME).orEmpty()
        if (collectionId == 0L) {
            finish()
            return
        }

        bindViews()
        setupRecycler()
        setupActions()
        setupSearch()
        applyWindowInsets()
        refreshCollectionImages()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshCollectionImages()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBackDetail).setOnClickListener { finish() }
        tvCollectionTitle = findViewById(R.id.tvCollectionTitle)
        searchContainer = findViewById(R.id.searchContainer)
        etCollectionSearch = findViewById(R.id.etCollectionSearch)
        btnVoiceCollectionSearch = findViewById(R.id.btnVoiceCollectionSearch)
        rvCollectionDetailImages = findViewById(R.id.rvCollectionDetailImages)
        tvDetailEmpty = findViewById(R.id.tvDetailEmpty)
        btnEditCollection = findViewById(R.id.btnEditCollection)
        btnDeleteCollection = findViewById(R.id.btnDeleteCollection)
        btnRemoveFromCollection = findViewById(R.id.btnRemoveFromCollection)
        fabAddToCollection = findViewById(R.id.fabAddToCollection)
        tvCollectionTitle.text = collectionName
    }

    private fun setupRecycler() {
        adapter = CollectionDetailImageAdapter(
            onImageClick = { image ->
                if (!isSelectionMode) openImageDetail(image)
            },
            onImageLongClick = { image ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    adapter.setSelectionMode(true)
                }
                adapter.select(image.id)
                updateSelectionUI()
            },
            onSelectionChanged = {
                updateSelectionUI()
            }
        )
        rvCollectionDetailImages.layoutManager = GridLayoutManager(this, 3)
        rvCollectionDetailImages.adapter = adapter
    }

    private fun setupActions() {
        btnEditCollection.setOnClickListener { showRenameDialog() }
        btnDeleteCollection.setOnClickListener { showDeleteDialog() }
        btnRemoveFromCollection.setOnClickListener { removeSelectedImages() }
        fabAddToCollection.setOnClickListener { openCollectionPicker() }
    }

    private fun setupSearch() {
        btnVoiceCollectionSearch.setOnClickListener {
            startVoiceSearchInCollection()
        }

        etCollectionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim().orEmpty()
                searchJob?.cancel()
                if (currentQuery.isBlank()) {
                    loadCollectionImages()
                } else {
                    val query = currentQuery
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        performCollectionSearch(query)
                    }
                }
            }
        })

        etCollectionSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchJob?.cancel()
                currentQuery = etCollectionSearch.text?.toString()?.trim().orEmpty()
                refreshCollectionImages()
                hideCollectionSearchKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun startVoiceSearchInCollection() {
        if (isVoiceSearching) return
        if (!hasRecordAudioPermission()) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 3001)
            Toast.makeText(this, getString(R.string.voice_permission_required), Toast.LENGTH_SHORT).show()
            return
        }

        isVoiceSearching = true
        btnVoiceCollectionSearch.isEnabled = false
        Toast.makeText(this, getString(R.string.voice_listening), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                SpeechRecognitionUiHelper.recognizeAndHandle(
                    context = this@CollectionDetailActivity,
                    delegate = speechRecognitionDelegate
                ) { text ->
                    etCollectionSearch.setText(text)
                    etCollectionSearch.setSelection(text.length)
                }
            } finally {
                onVoiceSearchFinished()
            }
        }
    }

    private fun onVoiceSearchFinished() {
        isVoiceSearching = false
        btnVoiceCollectionSearch.isEnabled = true
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshCollectionImages() {
        val query = currentQuery
        if (query.isBlank()) {
            loadCollectionImages()
        } else {
            performCollectionSearch(query)
        }
    }

    private fun loadCollectionImages() {
        loadImagesJob?.cancel()
        loadImagesJob = lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                ObjectBoxRepository.getImagesByCollection(
                    collectionId,
                    0L,
                    ObjectBoxRepository.count()
                )
            }
            if (currentQuery.isNotBlank()) return@launch
            renderImages(images, isSearchResult = false)
        }
    }

    private fun performCollectionSearch(query: String) {
        loadImagesJob?.cancel()
        loadImagesJob = lifecycleScope.launch {
            val filtered = withContext(Dispatchers.IO) {
                val all = ObjectBoxRepository.getImagesByCollection(
                    collectionId,
                    0L,
                    ObjectBoxRepository.count()
                )
                Log.d(TAG, "Calling SearchProcessor.search for collectionId=$collectionId, query='$query'")
                val resultUris = SearchProcessor.search(query).toSet()
                all.filter { it.uri in resultUris }
            }
            if (query != currentQuery) return@launch
            renderImages(filtered, isSearchResult = true)
        }
    }

    private fun renderImages(images: List<Image>, isSearchResult: Boolean) {
        currentItems = images
        adapter.submitItems(images)
        tvDetailEmpty.text = getString(
            if (isSearchResult) R.string.no_results else R.string.no_images_in_collection
        )
        tvDetailEmpty.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
        rvCollectionDetailImages.visibility = if (images.isEmpty()) View.GONE else View.VISIBLE
        if (images.isEmpty() && isSelectionMode) clearSelectionMode()
        updateSelectionUI()
    }

    private fun removeSelectedImages() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, getString(R.string.select_at_least_one_image), Toast.LENGTH_SHORT).show()
            return
        }
        selectedIds.forEach { imageId ->
            ObjectBoxRepository.removeImageFromCollection(imageId, collectionId)
        }
        setResult(Activity.RESULT_OK)
        clearSelectionMode()
        refreshCollectionImages()
    }

    private fun clearSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val selectedCount = adapter.getSelectedIds().size
        if (isSelectionMode && selectedCount == 0) {
            isSelectionMode = false
            adapter.setSelectionMode(false)
        }

        val hasSelection = selectedCount > 0
        if (hasSelection) hideCollectionSearchKeyboard()
        searchContainer.visibility = if (hasSelection) View.GONE else View.VISIBLE
        btnEditCollection.visibility = if (hasSelection) View.GONE else View.VISIBLE
        btnDeleteCollection.visibility = if (hasSelection) View.GONE else View.VISIBLE
        btnRemoveFromCollection.visibility = if (hasSelection) View.VISIBLE else View.GONE
        fabAddToCollection.visibility = if (hasSelection) View.GONE else View.VISIBLE
    }

    private fun openCollectionPicker() {
        val intent = Intent(this, CollectionPickerActivity::class.java).apply {
            putExtra(Constant.COLLECTION_ID, collectionId)
            putExtra(Constant.COLLECTION_NAME, collectionName)
        }
        pickerLauncher.launch(intent)
    }

    private fun hideCollectionSearchKeyboard() {
        etCollectionSearch.clearFocus()
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(etCollectionSearch.windowToken, 0)
    }

    private fun showRenameDialog() {
        val (inputView, input) = inflateDialogTextInput(
            getString(R.string.collection_name_hint),
            collectionName
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.rename_collection))
            .setView(inputView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text?.toString().orEmpty()
                val success = ObjectBoxRepository.renameCollection(collectionId, newName)
                if (!success) {
                    Toast.makeText(this, getString(R.string.invalid_collection_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                collectionName = newName.trim()
                tvCollectionTitle.text = collectionName
                setResult(Activity.RESULT_OK)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_collection))
            .setMessage(getString(R.string.delete_collection_keep_images_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                ObjectBoxRepository.deleteCollection(collectionId)
                setResult(Activity.RESULT_OK)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openImageDetail(image: Image) {
        val uris = currentItems.map { it.uri.toString() }
        val position = currentItems.indexOfFirst { it.id == image.id }.coerceAtLeast(0)
        val intent = Intent(this, ImageDetailActivity::class.java).apply {
            putStringArrayListExtra(Constant.IMAGE_URIS, ArrayList(uris))
            putExtra(Constant.IMAGE_POSITION, position)
        }
        imageDetailLauncher.launch(intent)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.collectionDetailRoot)
        val topContainer = findViewById<View>(R.id.topContainer)
        val fabAddToCollection = findViewById<FloatingActionButton>(R.id.fabAddToCollection)
        val topPadding = topContainer.paddingTop
        val recyclerBottomPadding = rvCollectionDetailImages.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topContainer.setPadding(
                topContainer.paddingLeft,
                bars.top + topPadding,
                topContainer.paddingRight,
                topContainer.paddingBottom
            )
            rvCollectionDetailImages.setPadding(
                rvCollectionDetailImages.paddingLeft,
                rvCollectionDetailImages.paddingTop,
                rvCollectionDetailImages.paddingRight,
                recyclerBottomPadding + bars.bottom
            )
            // Apply bottom margin to FAB to avoid gesture navigation bar
            val fabParams = fabAddToCollection.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            fabParams.bottomMargin = bars.bottom + 20
            fabAddToCollection.layoutParams = fabParams
            insets
        }
    }
}
