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

class CollectionPickerAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<CollectionPickerAdapter.PickerViewHolder>() {

    private val images = mutableListOf<Image>()
    private val selectedIds = mutableSetOf<Long>()

    inner class PickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivPickerImage)
        private val overlay: View = itemView.findViewById(R.id.viewSelectedOverlay)
        private val mark: TextView = itemView.findViewById(R.id.tvSelectedMark)

        fun bind(image: Image) {
            Glide.with(itemView.context)
                .load(image.uri)
                .centerCrop()
                .into(ivImage)

            val selected = image.id in selectedIds
            overlay.visibility = if (selected) View.VISIBLE else View.GONE
            mark.visibility = if (selected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                if (selected) selectedIds.remove(image.id) else selectedIds.add(image.id)
                notifyItemChanged(bindingAdapterPosition)
                onSelectionChanged(selectedIds.size)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection_picker_image, parent, false)
        return PickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount(): Int = images.size

    fun submitItems(items: List<Image>) {
        images.clear()
        images.addAll(items)
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()
}
