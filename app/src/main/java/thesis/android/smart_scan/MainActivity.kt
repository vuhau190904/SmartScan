package thesis.android.smart_scan

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import thesis.android.smart_scan.adapter.ImageAdapter
import thesis.android.smart_scan.processor.SearchProcessor
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.service.ScreenshotForegroundService
import thesis.android.smart_scan.service.mlkit.OCRService
import thesis.android.smart_scan.service.mlkit.TextEmbeddingService
import thesis.android.smart_scan.service.mlkit.TranslateService
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.ImageEventBus
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PAGE_SIZE = 30L
        private const val PREFETCH_DISTANCE = 8
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val GRID_SPAN_COUNT = 3
        private const val GRID_SPACING_DP = 4
    }

    // ── Views ──────────────────────────────────────────────────────────────

    private lateinit var etSearch: EditText
    private lateinit var tvResultCount: TextView
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

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        requestPermission()
        initServices()
        ScreenshotForegroundService.start(this)

        bindViews()
        setupRecyclerView()
        setupSearch()

        loadFirstPage()
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
        tvResultCount = findViewById(R.id.tvResultCount)
        rvImages = findViewById(R.id.rvImages)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter { uri -> openImageDetail(uri) }

        val layoutManager = StaggeredGridLayoutManager(
            GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL
        ).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }

        val spacingPx = (GRID_SPACING_DP * resources.displayMetrics.density).toInt()

        rvImages.apply {
            this.layoutManager = layoutManager
            adapter = imageAdapter
            addItemDecoration(UniformSpacingDecoration(spacingPx))
            addOnScrollListener(PaginationScrollListener())
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString()?.trim().orEmpty()
                if (query.isEmpty()) {
                    resetAndLoadAll()
                } else {
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
                if (query.isEmpty()) resetAndLoadAll() else performSearch(query)
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
        if (isSearchMode && currentQuery.isNotBlank()) performSearch(currentQuery)
        else loadFirstPage()
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
            searchResultsCache = allResults
            val firstPage = allResults.take(PAGE_SIZE.toInt())
            searchPageOffset = firstPage.size
            isLastPage = searchPageOffset >= allResults.size
            imageAdapter.resetItems(firstPage)
            updateSearchUI(allResults.size)
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
        startActivity(Intent(this, ImageDetailActivity::class.java).apply {
            putStringArrayListExtra(Constant.IMAGE_URIS, ArrayList(list.map { it.toString() }))
            putExtra(Constant.IMAGE_POSITION, position)
        })
    }

    // ── System ─────────────────────────────────────────────────────────────

    private fun initServices() {
        ObjectBoxRepository.init(this)
        MediaContentRepository.init(this)
        OCRService.init(this)
        TextEmbeddingService.init(this)
        TranslateService.init(Locale.getDefault().language)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 100)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
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
            val lm = recyclerView.layoutManager as StaggeredGridLayoutManager
            val lastVisible = lm.findLastVisibleItemPositions(null).maxOrNull() ?: return
            if (lastVisible >= lm.itemCount - PREFETCH_DISTANCE) {
                if (isSearchMode) loadMoreSearchResults() else loadMoreImages()
            }
        }
    }
}
