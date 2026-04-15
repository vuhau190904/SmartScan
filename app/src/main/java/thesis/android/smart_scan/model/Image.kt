package thesis.android.smart_scan.model

import android.net.Uri
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType
import thesis.android.smart_scan.converter.UriConverter

@Entity
data class Image(
    @Id
    var id: Long = 0,
    @Convert(converter = UriConverter::class, dbType = String::class)
    var uri: Uri,
    @HnswIndex(dimensions = 100, distanceType = VectorDistanceType.COSINE)
    var embeddingOCR: FloatArray,

    @HnswIndex(dimensions = 100, distanceType = VectorDistanceType.COSINE)
    var embeddingDescription: FloatArray? = null,
    var ocrText: String = "",
    var imageDescription: String? = null,
    var note: String? = null,
    var updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Image

        if (id != other.id) return false
        if (uri != other.uri) return false
        if (!embeddingOCR.contentEquals(other.embeddingOCR)) return false
        if (!embeddingDescription.contentEquals(other.embeddingDescription)) return false
        if (ocrText != other.ocrText) return false
        if (imageDescription != other.imageDescription) return false
        if (note != other.note) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + embeddingOCR.contentHashCode()
        result = 31 * result + embeddingDescription.contentHashCode()
        result = 31 * result + ocrText.hashCode()
        result = 31 * result + (imageDescription?.hashCode() ?: 0)
        result = 31 * result + (note?.hashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}