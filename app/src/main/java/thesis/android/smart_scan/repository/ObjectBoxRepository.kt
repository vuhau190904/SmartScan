package thesis.android.smart_scan.repository

import android.content.Context
import android.net.Uri
import io.objectbox.Box
import io.objectbox.BoxStore
import thesis.android.smart_scan.model.CollectionMembership
import thesis.android.smart_scan.model.CollectionMembership_
import thesis.android.smart_scan.model.Image
import thesis.android.smart_scan.model.ImageCollection
import thesis.android.smart_scan.model.ImageCollection_
import thesis.android.smart_scan.model.Image_
import thesis.android.smart_scan.model.MyObjectBox

object ObjectBoxRepository {
    lateinit var store: BoxStore
    lateinit var imageBox: Box<Image>
    lateinit var collectionBox: Box<ImageCollection>
    lateinit var membershipBox: Box<CollectionMembership>

    const val TAG = "ObjectBoxService"

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        imageBox = store.boxFor(Image::class.java)
        collectionBox = store.boxFor(ImageCollection::class.java)
        membershipBox = store.boxFor(CollectionMembership::class.java)
    }

    fun put(image: Image) {
        image.updatedAt = System.currentTimeMillis()
        imageBox.put(image)
    }

    fun getByUri(uri: Uri): Image? {
        return imageBox.all.firstOrNull { it.uri == uri }
    }

    fun getById(imageId: Long): Image? = imageBox.get(imageId)

    fun updateNoteByUri(uri: Uri, note: String?) {
        val image = getByUri(uri) ?: return
        image.note = note?.takeIf { it.isNotBlank() }
        image.updatedAt = System.currentTimeMillis()
        imageBox.put(image)
    }

    fun createCollection(name: String): ImageCollection? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        val now = System.currentTimeMillis()
        val collection = ImageCollection(
            name = trimmed,
            createdAt = now,
            updatedAt = now
        )
        collection.id = collectionBox.put(collection)
        return collection
    }

    fun renameCollection(collectionId: Long, newName: String): Boolean {
        val collection = collectionBox.get(collectionId) ?: return false
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        collection.name = trimmed
        collection.updatedAt = System.currentTimeMillis()
        collectionBox.put(collection)
        return true
    }

    fun deleteCollection(collectionId: Long) {
        val query = membershipBox.query()
            .equal(CollectionMembership_.collectionId, collectionId)
            .build()
        val memberships = query.find()
        query.close()
        if (memberships.isNotEmpty()) {
            membershipBox.remove(memberships)
        }
        collectionBox.remove(collectionId)
    }

    fun getAllCollections(): List<ImageCollection> {
        val query = collectionBox.query()
            .orderDesc(ImageCollection_.updatedAt)
            .build()
        val collections = query.find()
        query.close()
        return collections
    }

    fun addImageToCollection(imageId: Long, collectionId: Long): Boolean {
        if (!imageBox.contains(imageId) || !collectionBox.contains(collectionId)) return false
        val membershipKey = "${collectionId}_$imageId"
        val exists = membershipBox.all.any { it.membershipKey == membershipKey }
        if (exists) return false

        membershipBox.put(
            CollectionMembership(
                collectionId = collectionId,
                imageId = imageId,
                membershipKey = membershipKey,
                addedAt = System.currentTimeMillis()
            )
        )

        collectionBox.get(collectionId)?.let {
            it.updatedAt = System.currentTimeMillis()
            collectionBox.put(it)
        }
        return true
    }

    fun removeImageFromCollection(imageId: Long, collectionId: Long): Boolean {
        val membershipKey = "${collectionId}_$imageId"
        val membership = membershipBox.all.firstOrNull { it.membershipKey == membershipKey }
            ?: return false

        membershipBox.remove(membership)
        collectionBox.get(collectionId)?.let {
            it.updatedAt = System.currentTimeMillis()
            collectionBox.put(it)
        }
        return true
    }

    fun getCollectionsForImage(imageId: Long): List<ImageCollection> {
        val query = membershipBox.query()
            .equal(CollectionMembership_.imageId, imageId)
            .build()
        val collectionIds = query.find().map { it.collectionId }
        query.close()
        if (collectionIds.isEmpty()) return emptyList()
        return collectionIds.mapNotNull { collectionBox.get(it) }
    }

    fun getImageIdsInCollection(collectionId: Long): List<Long> {
        val query = membershipBox.query()
            .equal(CollectionMembership_.collectionId, collectionId)
            .build()
        val imageIds = query.find().map { it.imageId }
        query.close()
        return imageIds
    }

    fun getImagesByCollection(collectionId: Long, offset: Long, limit: Long): List<Image> {
        val imageIds = getImageIdsInCollection(collectionId)
        if (imageIds.isEmpty()) return emptyList()
        return imageIds.mapNotNull { imageBox.get(it) }
            .sortedByDescending { it.id }
            .drop(offset.toInt())
            .take(limit.toInt())
    }

    fun getImagesNotInCollection(collectionId: Long, offset: Long, limit: Long): List<Image> {
        val existingIds = getImageIdsInCollection(collectionId).toSet()
        val query = imageBox.query()
            .orderDesc(Image_.id)
            .build()
        val images = query.find()
            .filterNot { it.id in existingIds }
            .drop(offset.toInt())
            .take(limit.toInt())
        query.close()
        return images
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
        val query = imageBox.query()
            .orderDesc(Image_.id)
            .build()
        val uris = query.find(offset, limit).map { it.uri }
        query.close()
        return uris
    }

    fun getImagesPaged(offset: Long, limit: Long): List<Image> {
        val query = imageBox.query()
            .orderDesc(Image_.id)
            .build()
        val images = query.find(offset, limit)
        query.close()
        return images
    }

    fun getCollectionPreviewUris(collectionId: Long, limit: Int = 3): List<Uri> {
        return getImagesByCollection(collectionId, 0L, limit.toLong()).map { it.uri }
    }

    fun count(): Long = imageBox.count()
}