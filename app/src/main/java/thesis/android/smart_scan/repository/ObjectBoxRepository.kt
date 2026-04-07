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

    fun search(embedding: FloatArray, limit: Int): MutableList<Uri> {
        val uries: MutableList<Uri> = mutableListOf()
        val query = imageBox.query(Image_.embedding.nearestNeighbors(embedding, limit)).build()
        val results: List<ObjectWithScore<Image>> = query.findWithScores()

        for (result in results) {
            val image = result.get()
            val score = result.score
            if(score <= 0.3) {
                uries.add(image.uri)
            }
        }
        query.close()
        return uries
    }

    fun getAllUris(): List<Uri> {
        return imageBox.all.map { it.uri }
    }

    fun getAll() {
        val results = imageBox.all
        for (result in results) {
            Log.i(TAG, result.toString())
        }
    }

}