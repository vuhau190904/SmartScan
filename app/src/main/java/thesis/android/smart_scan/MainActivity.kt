package thesis.android.smart_scan

import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import thesis.android.smart_scan.adapter.CollectionCardAdapter
import thesis.android.smart_scan.adapter.CollectionCardItem
import thesis.android.smart_scan.adapter.ImageAdapter
import thesis.android.smart_scan.config.AppConfig
import thesis.android.smart_scan.model.ImageCollection
import thesis.android.smart_scan.processor.SearchProcessor
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.ScreenshotForegroundService
import thesis.android.smart_scan.service.mlkit.ImageDescriptionService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionDelegate
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionService
import thesis.android.smart_scan.service.mlkit.speech_to_text.SpeechRecognitionUiHelper
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.inflateDialogTextInput
import thesis.android.smart_scan.util.ImageEventBus
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PAGE_SIZE = 30L
        private const val PREFETCH_DISTANCE = 8
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val GRID_SPAN_COUNT = 3
        private const val GRID_SPACING_DP = 6
        private const val REQUEST_RECORD_AUDIO = 2001
    }

    // ── Views ──────────────────────────────────────────────────────────────

    private lateinit var etSearch: EditText
    private lateinit var btnVoiceSearch: ImageButton
    private lateinit var tvResultCount: TextView
    private lateinit var collectionHeader: View
    private lateinit var screenshotsSectionHeader: View
    private lateinit var rvCollections: RecyclerView
    private lateinit var btnAddCollection: ImageButton
    private lateinit var collectionCardAdapter: CollectionCardAdapter
    private lateinit var rvImages: RecyclerView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var imageAdapter: ImageAdapter

    // ── Browse pagination state ────────────────────────────────────────────

    private var currentOffset = 0L
    private var isLoadingMore = false
    private var isLastPage = false

    // ── Search state ───────────────────────────────────────────────────────

    private var isSearchMode = false
    private var currentQuery = ""
    private var searchJob: Job? = null
    private var searchResultsCache: List<Uri> = emptyList()
    private var searchPageOffset = 0
    private var isVoiceSearching = false

    private lateinit var speechRecognitionDelegate: SpeechRecognitionDelegate

    private val imageDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshCurrentView()
            renderCollections()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechRecognitionDelegate = SpeechRecognitionDelegate(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        requestPermission()
        lifecycleScope.launch {
            initServices()
            ScreenshotForegroundService.start(this@MainActivity)
        }

        bindViews()
        setupCollectionsRecyclerView()
        setupRecyclerView()
        setupSearch()
        setupCollectionActions()

        loadFirstPage()
        renderCollections()
        observeNewImages()
    }

    override fun onStart() {
        super.onStart()
        refreshCurrentView()
    }

    // ── Initialization ─────────────────────────────────────────────────────

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun bindViews() {
        etSearch = findViewById(R.id.etSearch)
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch)
        tvResultCount = findViewById(R.id.tvResultCount)
        collectionHeader = findViewById(R.id.collectionHeader)
        screenshotsSectionHeader = findViewById(R.id.screenshotsSectionHeader)
        rvCollections = findViewById(R.id.rvCollections)
        btnAddCollection = findViewById(R.id.btnAddCollection)
        rvImages = findViewById(R.id.rvImages)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
    }

    private fun setupCollectionsRecyclerView() {
        collectionCardAdapter = CollectionCardAdapter { collection ->
            val intent = Intent(this, CollectionDetailActivity::class.java).apply {
                putExtra(Constant.COLLECTION_ID, collection.id)
                putExtra(Constant.COLLECTION_NAME, collection.name)
            }
            imageDetailLauncher.launch(intent)
        }
        rvCollections.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = collectionCardAdapter
        }
    }

    private fun setupCollectionActions() {
        btnAddCollection.setOnClickListener { showCreateCollectionDialog() }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter { uri -> openImageDetail(uri) }

        val layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)

        val spacingPx = (GRID_SPACING_DP * resources.displayMetrics.density).toInt()

        rvImages.apply {
            this.layoutManager = layoutManager
            adapter = imageAdapter
            addItemDecoration(UniformSpacingDecoration(spacingPx))
            addOnScrollListener(PaginationScrollListener())
        }
    }

    private fun setupSearch() {
        btnVoiceSearch.setOnClickListener {
            startVoiceSearch()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString()?.trim().orEmpty()
                if (query.isEmpty()) {
                    setCollectionsVisible(true)
                    resetAndLoadAll()
                } else {
                    setCollectionsVisible(false)
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        performSearch(query)
                    }
                }
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchJob?.cancel()
                val query = etSearch.text.toString().trim()
                hideKeyboard()
                if (query.isEmpty()) {
                    setCollectionsVisible(true)
                    resetAndLoadAll()
                } else {
                    setCollectionsVisible(false)
                    performSearch(query)
                }
                true
            } else false
        }
    }

    // ── Real-time refresh ──────────────────────────────────────────────────

    private fun observeNewImages() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ImageEventBus.newImageFlow.collect { refreshCurrentView() }
            }
        }
    }

    private fun refreshCurrentView() {
        if (isSearchMode && currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        } else {
            loadFirstPage()
        }
    }

    // ── Browse pagination ──────────────────────────────────────────────────

    private fun loadFirstPage() {
        currentOffset = 0L
        isLastPage = false
        isLoadingMore = false
        isSearchMode = false
        showLoading(true)

        lifecycleScope.launch {
            val page = ObjectBoxRepository.getUrisPaged(0L, PAGE_SIZE)
            showLoading(false)
            isLastPage = page.size < PAGE_SIZE
            currentOffset = page.size.toLong()
            imageAdapter.resetItems(page)
            updateBrowseCountUI(page.size, isFirstPage = true)
        }
    }

    private fun loadMoreImages() {
        isLoadingMore = true
        lifecycleScope.launch {
            val page = ObjectBoxRepository.getUrisPaged(currentOffset, PAGE_SIZE)
            isLoadingMore = false
            if (page.isEmpty()) { isLastPage = true; return@launch }
            isLastPage = page.size < PAGE_SIZE
            currentOffset += page.size
            imageAdapter.appendItems(page)
            updateBrowseCountUI(imageAdapter.itemCount, isFirstPage = false)
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────

    private fun performSearch(query: String) {
        isSearchMode = true
        currentQuery = query
        searchResultsCache = emptyList()
        searchPageOffset = 0
        isLastPage = false
        isLoadingMore = false
        showLoading(true)

        lifecycleScope.launch {
            val allResults = SearchProcessor.search(query)
            showLoading(false)
            val filteredResults = allResults
            searchResultsCache = filteredResults
            val firstPage = filteredResults.take(PAGE_SIZE.toInt())
            searchPageOffset = firstPage.size
            isLastPage = searchPageOffset >= filteredResults.size
            imageAdapter.resetItems(firstPage)
            updateSearchUI(filteredResults.size)
        }
    }

    private fun loadMoreSearchResults() {
        if (isLoadingMore || searchPageOffset >= searchResultsCache.size) return
        isLoadingMore = true
        val nextPage = searchResultsCache.drop(searchPageOffset).take(PAGE_SIZE.toInt())
        searchPageOffset += nextPage.size
        isLastPage = searchPageOffset >= searchResultsCache.size
        isLoadingMore = false
        imageAdapter.appendItems(nextPage)
    }

    private fun resetAndLoadAll() {
        isSearchMode = false
        currentQuery = ""
        searchResultsCache = emptyList()
        searchPageOffset = 0
        setCollectionsVisible(true)
        loadFirstPage()
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private fun updateBrowseCountUI(displayedCount: Int, isFirstPage: Boolean) {
        val total = ObjectBoxRepository.count().toInt()
        if (displayedCount == 0 && isFirstPage) {
            rvImages.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyMessage.text = getString(R.string.no_indexed_images)
            tvResultCount.text = ""
        } else {
            rvImages.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            tvResultCount.text = getString(R.string.all_images, total)
        }
    }

    private fun updateSearchUI(totalCount: Int) {
        if (totalCount == 0) {
            rvImages.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyMessage.text = getString(R.string.no_results)
            tvResultCount.text = ""
        } else {
            rvImages.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            tvResultCount.text = getString(R.string.result_count, totalCount)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    private fun openImageDetail(uri: Uri) {
        val list = imageAdapter.currentList
        val position = list.indexOf(uri).coerceAtLeast(0)
        imageDetailLauncher.launch(Intent(this, ImageDetailActivity::class.java).apply {
            putStringArrayListExtra(Constant.IMAGE_URIS, ArrayList(list.map { it.toString() }))
            putExtra(Constant.IMAGE_POSITION, position)
        })
    }

    // ── System ─────────────────────────────────────────────────────────────

    private suspend fun initServices() {
        ObjectBoxRepository.init(this)
        MediaContentRepository.init(this)
        OCRService.init(this)
        TextEmbeddingService.init(this)
        TranslateService.init(Locale.getDefault().language)
        runCatching { ImageDescriptionService.init(this) }
            .onFailure { e ->
                Log.w(TAG, "ImageDescriptionService init thất bại trên thiết bị này: ${e.message}", e)
            }
        val speechLocale = Locale.forLanguageTag(AppConfig().userLanguage)
        runCatching { SpeechRecognitionService.init(speechLocale) }
            .onFailure { e ->
                Log.w(TAG, "SpeechRecognitionService init thất bại trên thiết bị này: ${e.message}", e)
            }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun requestPermission() {
        val needsAudio = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf(android.Manifest.permission.READ_MEDIA_IMAGES)
            if (needsAudio) permissions += android.Manifest.permission.RECORD_AUDIO
            requestPermissions(permissions.toTypedArray(), 100)
        } else {
            val permissions = mutableListOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            if (needsAudio) permissions += android.Manifest.permission.RECORD_AUDIO
            requestPermissions(permissions.toTypedArray(), 100)
        }
    }

    private fun startVoiceSearch() {
        if (isVoiceSearching) return
        if (!hasRecordAudioPermission()) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            Toast.makeText(this, getString(R.string.voice_permission_required), Toast.LENGTH_SHORT).show()
            return
        }

        isVoiceSearching = true
        btnVoiceSearch.isEnabled = false
        Toast.makeText(this, getString(R.string.voice_listening), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                SpeechRecognitionUiHelper.recognizeAndHandle(
                    context = this@MainActivity,
                    delegate = speechRecognitionDelegate,
                    logTag = TAG
                ) { text ->
                    etSearch.setText(text)
                    etSearch.setSelection(text.length)
                }
            } finally {
                onVoiceSearchFinished()
            }
        }
    }

    private fun onVoiceSearchFinished() {
        isVoiceSearching = false
        btnVoiceSearch.isEnabled = true
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderCollections() {
        val collections = ObjectBoxRepository.getAllCollections()
        val cardItems = collections.map { collection ->
            CollectionCardItem(
                collection = collection,
                imageCount = ObjectBoxRepository.getImageIdsInCollection(collection.id).size,
                previewUris = ObjectBoxRepository.getCollectionPreviewUris(collection.id, 3)
            )
        }
        collectionCardAdapter.submitItems(cardItems)
    }

    private fun showCreateCollectionDialog() {
        val (inputView, input) = inflateDialogTextInput(getString(R.string.collection_name_hint))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.create_collection))
            .setView(inputView)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val collection = ObjectBoxRepository.createCollection(input.text?.toString().orEmpty())
                if (collection == null) {
                    Toast.makeText(this, getString(R.string.invalid_collection_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                renderCollections()
                refreshCurrentView()
                openCollectionDetail(collection)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setCollectionsVisible(visible: Boolean) {
        val state = if (visible) View.VISIBLE else View.GONE
        collectionHeader.visibility = state
        rvCollections.visibility = state
        screenshotsSectionHeader.visibility = state
    }

    private fun openCollectionDetail(collection: ImageCollection) {
        val intent = Intent(this, CollectionDetailActivity::class.java).apply {
            putExtra(Constant.COLLECTION_ID, collection.id)
            putExtra(Constant.COLLECTION_NAME, collection.name)
        }
        imageDetailLauncher.launch(intent)
    }

    // ── Inner classes ──────────────────────────────────────────────────────

    private class UniformSpacingDecoration(
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) = outRect.set(spacing, spacing, spacing, spacing)
    }

    private inner class PaginationScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0 || isLoadingMore || isLastPage) return
            val lm = recyclerView.layoutManager as GridLayoutManager
            val lastVisible = lm.findLastVisibleItemPosition()
            if (lastVisible >= lm.itemCount - PREFETCH_DISTANCE) {
                if (isSearchMode) loadMoreSearchResults() else loadMoreImages()
            }
        }
    }
}
