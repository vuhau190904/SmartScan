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
    var embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Image

        if (id != other.id) return false
        if (uri != other.uri) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (uri?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}