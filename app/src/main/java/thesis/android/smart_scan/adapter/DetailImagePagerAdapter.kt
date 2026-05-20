package thesis.android.smart_scan.adapter

import android.net.Uri
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import thesis.android.smart_scan.R

class DetailImagePagerAdapter(
    private val uris: List<Uri>,
    private val firstMeasuredUri: Uri? = null,
    private val onFirstImageReady: (() -> Unit)? = null
) : RecyclerView.Adapter<DetailImagePagerAdapter.PageViewHolder>() {

    private var firstImageReported = false

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivPageImage)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .fitCenter()
                .thumbnail(0.15f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        reportFirstImageReadyIfNeeded(uri)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        reportFirstImageReadyIfNeeded(uri)
                        return false
                    }
                })
                .into(imageView)
        }

        private fun reportFirstImageReadyIfNeeded(uri: Uri) {
            if (firstImageReported || uri != firstMeasuredUri) return
            firstImageReported = true
            imageView.post {
                onFirstImageReady?.invoke()
            }
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
