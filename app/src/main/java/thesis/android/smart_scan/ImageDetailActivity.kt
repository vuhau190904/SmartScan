package thesis.android.smart_scan

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import thesis.android.smart_scan.repository.MediaContentRepository
import thesis.android.smart_scan.util.Constant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_image_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uriString = intent.getStringExtra(Constant.IMAGE_URI) ?: run {
            finish()
            return
        }
        val uri = uriString.toUri()

        val ivDetailImage = findViewById<ImageView>(R.id.ivDetailImage)
        val tvFileName = findViewById<TextView>(R.id.tvDetailFileName)
        val tvDate = findViewById<TextView>(R.id.tvDetailDate)
        val tvSize = findViewById<TextView>(R.id.tvDetailSize)
        val tvDimensions = findViewById<TextView>(R.id.tvDetailDimensions)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        Glide.with(this)
            .load(uri)
            .fitCenter()
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .into(ivDetailImage)

        val info = MediaContentRepository.getImageDetails(uri)
        if (info != null) {
            tvFileName.text = info.displayName

            val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
                .format(Date(info.dateAdded * 1000L))
            tvDate.text = dateStr

            tvSize.text = formatFileSize(info.size)

            if (info.width > 0 && info.height > 0) {
                tvDimensions.text = "${info.width} × ${info.height} px"
            } else {
                tvDimensions.text = "Không rõ"
            }
        } else {
            tvFileName.text = uri.lastPathSegment ?: "Không rõ"
            tvDate.text = "Không rõ"
            tvSize.text = "Không rõ"
            tvDimensions.text = "Không rõ"
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
