package thesis.android.smart_scan.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import thesis.android.smart_scan.R

class ImageAdapter(
    private val onItemClick: (Uri) -> Unit
) : ListAdapter<Uri, ImageAdapter.ImageViewHolder>(URI_DIFF) {

    companion object {
        private val URI_DIFF = object : DiffUtil.ItemCallback<Uri>() {
            override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        }
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivImage)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(120))
                .placeholder(R.drawable.ic_image_placeholder)
                .into(ivImage)

            itemView.setOnClickListener { onItemClick(uri) }
        }
    }

    fun resetItems(uris: List<Uri>) = submitList(uris)

    fun appendItems(newUris: List<Uri>) {
        if (newUris.isEmpty()) return
        submitList(currentList.toMutableList().apply { addAll(newUris) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.ivImage)
    }
}
