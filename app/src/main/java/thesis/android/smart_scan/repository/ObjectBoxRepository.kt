package thesis.android.smart_scan.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.ObjectWithScore
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.model.Image_
import thesis.android.smart_scan.model.MyObjectBox

object ObjectBoxRepository {
    lateinit var store: BoxStore
    lateinit var imageBox: Box<Image>

    const val TAG = "ObjectBoxService"

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        imageBox = store.boxFor(Image::class.java)
    }

    fun put(image: Image) {
        imageBox.put(image)
    }

    fun getByUri(uri: Uri): Image? {
        return imageBox.all.firstOrNull { it.uri == uri }
    }

    fun updateNoteByUri(uri: Uri, note: String?) {
        val image = getByUri(uri) ?: return
        image.note = note?.takeIf { it.isNotBlank() }
        imageBox.put(image)
    }

    fun search(queryVector: FloatArray, limit: Int): List<Uri> {
        val queryOCR = imageBox.query()
            .nearestNeighbors(Image_.embeddingOCR, queryVector, limit)
            .build()

        val queryDesc = imageBox.query()
            .nearestNeighbors(Image_.embeddingDescription, queryVector, limit)
            .build()

        val finalUris = (queryOCR.findWithScores() + queryDesc.findWithScores())
            .filter { it.score <= 0.3 }
            .groupBy { it.get().id }
            .map { (_, results) ->
                results.minBy { it.score }
            }
            .sortedBy { it.score }
            .take(limit)
            .mapNotNull { it.get().uri }

        queryOCR.close()
        queryDesc.close()

        return finalUris
    }

    fun getUrisPaged(offset: Long, limit: Long): List<Uri> {
        return imageBox.query()
            .orderDesc(Image_.id)
            .build()
            .find(offset, limit)
            .map { it.uri }
    }

    fun count(): Long = imageBox.count()

}