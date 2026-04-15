package thesis.android.smart_scan.adapter

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import thesis.android.smart_scan.R
import thesis.android.smart_scan.model.ImageCollection

data class CollectionCardItem(
    val collection: ImageCollection,
    val imageCount: Int,
    val previewUris: List<Uri>
)

class CollectionCardAdapter(
    private val onClick: (ImageCollection) -> Unit
) : RecyclerView.Adapter<CollectionCardAdapter.CollectionCardViewHolder>() {

    private val items = mutableListOf<CollectionCardItem>()
    private val pastelColors = listOf(
        "#FCE4EC".toColorInt(),
        "#E3F2FD".toColorInt(),
        "#FFF9C4".toColorInt(),
        "#E8F5E9".toColorInt(),
        "#F3E5F5".toColorInt()
    )

    inner class CollectionCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardCollection)
        private val tvCollectionName: TextView = itemView.findViewById(R.id.tvCollectionName)
        private val tvCountBadge: TextView = itemView.findViewById(R.id.tvCountBadge)
        private val ivPreviewFront: ImageView = itemView.findViewById(R.id.ivPreviewFront)
        private val ivPreviewMiddle: ImageView = itemView.findViewById(R.id.ivPreviewMiddle)
        private val ivPreviewBack: ImageView = itemView.findViewById(R.id.ivPreviewBack)

        fun bind(item: CollectionCardItem, position: Int) {
            card.setCardBackgroundColor(pastelColors[position % pastelColors.size])
            tvCollectionName.text = item.collection.name.replaceFirstChar { it.uppercase() }
            tvCountBadge.text = if (item.imageCount > 999) "999+" else item.imageCount.toString()
            bindPreview(ivPreviewFront, item.previewUris.getOrNull(0))
            bindPreview(ivPreviewMiddle, item.previewUris.getOrNull(1))
            bindPreview(ivPreviewBack, item.previewUris.getOrNull(2))
            itemView.setOnClickListener { onClick(item.collection) }
        }

        private fun bindPreview(imageView: ImageView, uri: Uri?) {
            if (uri == null) {
                imageView.setImageResource(R.drawable.ic_image_placeholder)
                imageView.setColorFilter(Color.argb(85, 255, 255, 255))
                return
            }
            imageView.clearColorFilter()
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .into(imageView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection_card, parent, false)
        return CollectionCardViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CollectionCardViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    fun submitItems(newItems: List<CollectionCardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
