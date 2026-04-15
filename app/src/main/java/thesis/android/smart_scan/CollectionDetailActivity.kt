package thesis.android.smart_scan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import thesis.android.smart_scan.adapter.CollectionDetailImageAdapter
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.processor.SearchProcessor
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.util.Constant
import thesis.android.smart_scan.util.inflateDialogTextInput

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var tvCollectionTitle: TextView
    private lateinit var etCollectionSearch: EditText
    private lateinit var rvCollectionDetailImages: RecyclerView
    private lateinit var tvDetailEmpty: TextView
    private lateinit var selectionBar: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnRemoveFromCollection: MaterialButton
    private lateinit var btnEditCollection: ImageButton
    private lateinit var btnDeleteCollection: ImageButton
    private lateinit var fabAddToCollection: FloatingActionButton

    private lateinit var adapter: CollectionDetailImageAdapter

    private var collectionId: Long = 0L
    private var collectionName: String = ""
    private var currentItems: List<Image> = emptyList()
    private var isSelectionMode = false
    private var currentQuery = ""

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            loadImages()
        }
    }

    private val imageDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            loadImages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        loadImages()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBackDetail).setOnClickListener { finish() }
        tvCollectionTitle = findViewById(R.id.tvCollectionTitle)
        etCollectionSearch = findViewById(R.id.etCollectionSearch)
        rvCollectionDetailImages = findViewById(R.id.rvCollectionDetailImages)
        tvDetailEmpty = findViewById(R.id.tvDetailEmpty)
        selectionBar = findViewById(R.id.selectionBar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnRemoveFromCollection = findViewById(R.id.btnRemoveFromCollection)
        btnEditCollection = findViewById(R.id.btnEditCollection)
        btnDeleteCollection = findViewById(R.id.btnDeleteCollection)
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
            }
        )
        rvCollectionDetailImages.layoutManager = GridLayoutManager(this, 3)
        rvCollectionDetailImages.adapter = adapter
    }

    private fun setupActions() {
        btnEditCollection.setOnClickListener { showRenameDialog() }
        btnDeleteCollection.setOnClickListener { showDeleteDialog() }
        btnRemoveFromCollection.setOnClickListener { removeSelectedImages() }
        fabAddToCollection.setOnClickListener {
            val intent = Intent(this, CollectionPickerActivity::class.java).apply {
                putExtra(Constant.COLLECTION_ID, collectionId)
                putExtra(Constant.COLLECTION_NAME, collectionName)
            }
            pickerLauncher.launch(intent)
        }
    }

    private fun setupSearch() {
        etCollectionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim().orEmpty()
                loadImages()
            }
        })
    }

    private fun loadImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val all = ObjectBoxRepository.getImagesByCollection(collectionId, 0L, ObjectBoxRepository.count())
            val filtered = if (currentQuery.isBlank()) {
                all
            } else {
                val resultUris = SearchProcessor.search(currentQuery).toSet()
                all.filter { it.uri in resultUris }
            }
            withContext(Dispatchers.Main) {
                currentItems = filtered
                adapter.submitItems(filtered)
                tvDetailEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                rvCollectionDetailImages.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                if (filtered.isEmpty() && isSelectionMode) clearSelectionMode()
                updateSelectionUI()
            }
        }
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
        loadImages()
    }

    private fun clearSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val selectedCount = adapter.getSelectedIds().size
        selectionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        tvSelectedCount.text = getString(R.string.selected_count, selectedCount)
        btnEditCollection.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        btnDeleteCollection.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        fabAddToCollection.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        if (isSelectionMode && selectedCount == 0) clearSelectionMode()
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
            insets
        }
    }
}

