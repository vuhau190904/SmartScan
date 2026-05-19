package thesis.android.smart_scan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import thesis.android.smart_scan.R
import thesis.android.smart_scan.model.Image

class CollectionDetailImageAdapter(
    private val onImageClick: (Image) -> Unit,
    private val onImageLongClick: (Image) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<CollectionDetailImageAdapter.DetailViewHolder>() {

    private val items = mutableListOf<Image>()
    private val selectedIds = mutableSetOf<Long>()
    private var isSelectionMode = false

    inner class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivCollectionDetailImage)
        private val overlay: View = itemView.findViewById(R.id.viewSelectionOverlay)
        private val mark: TextView = itemView.findViewById(R.id.tvSelectionMark)

        fun bind(image: Image) {
            Glide.with(itemView.context)
                .load(image.uri)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .into(ivImage)

            val selected = image.id in selectedIds
            overlay.visibility = if (selected) View.VISIBLE else View.GONE
            mark.visibility = if (selected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(image.id)
                } else {
                    onImageClick(image)
                }
            }
            itemView.setOnLongClickListener {
                onImageLongClick(image)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection_detail_image, parent, false)
        return DetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(images: List<Image>) {
        val previousSelectedCount = selectedIds.size
        items.clear()
        items.addAll(images)
        selectedIds.retainAll(images.map { it.id }.toSet())
        notifyDataSetChanged()
        if (selectedIds.size != previousSelectedCount) onSelectionChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
    }

    fun select(imageId: Long) {
        if (selectedIds.add(imageId)) {
            notifyDataSetChanged()
            onSelectionChanged()
        }
    }

    fun toggleSelection(imageId: Long) {
        if (imageId in selectedIds) selectedIds.remove(imageId) else selectedIds.add(imageId)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun getSelectedIds(): Set<Long> = selectedIds
}
