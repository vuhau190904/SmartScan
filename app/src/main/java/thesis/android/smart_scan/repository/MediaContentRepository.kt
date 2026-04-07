package thesis.android.smart_scan.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class ImageInfo(
    val displayName: String,
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int
)

object MediaContentRepository {
    lateinit var contentResolver: ContentResolver

    fun init(context: Context) {
        contentResolver = context.applicationContext.contentResolver
    }

    fun getImageDetails(uri: Uri): ImageInfo? {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                    val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                    ImageInfo(name ?: "Không rõ", size, date, width, height)
                } else null
            }
        } catch (e: Exception) {
            Log.e("MediaContentRepository", "Lỗi khi lấy thông tin ảnh: $uri", e)
            null
        }
    }
}
