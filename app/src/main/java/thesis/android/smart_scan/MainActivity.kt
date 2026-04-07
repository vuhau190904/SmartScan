package thesis.android.smart_scan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
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
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var imageAdapter: ImageAdapter
    private lateinit var rvImages: RecyclerView
    private lateinit var tvResultCount: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestPermission()
        initServices()
        ScreenshotForegroundService.start(this)

        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnSearch = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSearch)
        tvResultCount = findViewById(R.id.tvResultCount)
        rvImages = findViewById(R.id.rvImages)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        imageAdapter = ImageAdapter { uri -> openImageDetail(uri) }
        rvImages.layoutManager = GridLayoutManager(this, 3)
        rvImages.adapter = imageAdapter

        btnSearch.setOnClickListener {
            triggerSearch(etSearch)
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch(etSearch)
                true
            } else false
        }

        loadAllImages()
    }

    private fun loadAllImages() {
        showLoading(true)
        lifecycleScope.launch {
            val allUris = ObjectBoxRepository.getAllUris()
            showLoading(false)
            updateUI(allUris, isSearch = false)
        }
    }

    private fun triggerSearch(etSearch: EditText) {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            loadAllImages()
            return
        }
        hideKeyboard(etSearch)
        showLoading(true)
        lifecycleScope.launch {
            val results = SearchProcessor.search(query)
            showLoading(false)
            updateUI(results, isSearch = true)
        }
    }

    private fun updateUI(uris: List<Uri>, isSearch: Boolean) {
        imageAdapter.submitList(uris)
        if (uris.isEmpty()) {
            rvImages.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyMessage.text = if (isSearch) {
                getString(R.string.no_results)
            } else {
                getString(R.string.no_indexed_images)
            }
            tvResultCount.text = ""
        } else {
            rvImages.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            tvResultCount.text = if (isSearch) {
                getString(R.string.result_count, uris.size)
            } else {
                getString(R.string.all_images, uris.size)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun openImageDetail(uri: Uri) {
        val intent = Intent(this, ImageDetailActivity::class.java).apply {
            putExtra(Constant.IMAGE_URI, uri.toString())
        }
        startActivity(intent)
    }

    private fun initServices() {
        ObjectBoxRepository.init(this)
        MediaContentRepository.init(this)
        OCRService.init(this)
        TextEmbeddingService.init(this)
        TranslateService.init(Locale.getDefault().language)
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 100)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }
}
