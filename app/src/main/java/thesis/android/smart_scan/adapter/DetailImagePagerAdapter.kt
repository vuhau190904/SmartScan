package thesis.android.smart_scan.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import thesis.android.smart_scan.R

class DetailImagePagerAdapter(
    private val uris: List<Uri>
) : RecyclerView.Adapter<DetailImagePagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivPageImage)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .fitCenter()
                .thumbnail(0.15f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(imageView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_image, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(uris[position])
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.imageView)
        holder.imageView.setImageDrawable(null)
    }

    override fun getItemCount(): Int = uris.size
}
