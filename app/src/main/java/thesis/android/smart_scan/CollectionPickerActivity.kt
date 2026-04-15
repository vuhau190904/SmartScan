package thesis.android.smart_scan

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import thesis.android.smart_scan.adapter.CollectionPickerAdapter
import thesis.android.smart_scan.repository.ObjectBoxRepository
import thesis.android.smart_scan.util.Constant

class CollectionPickerActivity : AppCompatActivity() {

    private lateinit var rvPickerImages: RecyclerView
    private lateinit var btnAddSelected: MaterialButton
    private lateinit var tvPickerTitle: TextView
    private lateinit var adapter: CollectionPickerAdapter

    private var collectionId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_collection_picker)

        collectionId = intent.getLongExtra(Constant.COLLECTION_ID, 0L)
        if (collectionId == 0L) {
            finish()
            return
        }

        rvPickerImages = findViewById(R.id.rvPickerImages)
        btnAddSelected = findViewById(R.id.btnAddSelected)
        tvPickerTitle = findViewById(R.id.tvPickerTitle)
        findViewById<ImageButton>(R.id.btnBackPicker).setOnClickListener { finish() }
        applyWindowInsets()

        val collectionName = intent.getStringExtra(Constant.COLLECTION_NAME).orEmpty()
        if (collectionName.isNotBlank()) {
            tvPickerTitle.text = getString(R.string.add_images_to_collection_title, collectionName)
        }

        adapter = CollectionPickerAdapter { selectedCount ->
            btnAddSelected.text = if (selectedCount == 0) {
                getString(R.string.add)
            } else {
                getString(R.string.add_selected_count, selectedCount)
            }
        }

        rvPickerImages.layoutManager = GridLayoutManager(this, 3)
        rvPickerImages.adapter = adapter

        btnAddSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.select_at_least_one_image), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedIds.forEach { imageId ->
                ObjectBoxRepository.addImageToCollection(imageId, collectionId)
            }
            setResult(Activity.RESULT_OK)
            finish()
        }

        loadImages()
    }

    private fun loadImages() {
        val images = ObjectBoxRepository.getImagesNotInCollection(
            collectionId = collectionId,
            offset = 0L,
            limit = ObjectBoxRepository.count()
        )
        adapter.submitItems(images)
    }

    private fun applyWindowInsets() {
        val root = findViewById<android.view.View>(R.id.pickerRoot)
        val topBar = findViewById<android.view.View>(R.id.pickerTopBar)
        val topBarInitialTop = topBar.paddingTop
        val recyclerInitialBottom = rvPickerImages.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(
                topBar.paddingLeft,
                bars.top + topBarInitialTop,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            rvPickerImages.setPadding(
                rvPickerImages.paddingLeft,
                rvPickerImages.paddingTop,
                rvPickerImages.paddingRight,
                bars.bottom + recyclerInitialBottom
            )
            insets
        }
    }
}
