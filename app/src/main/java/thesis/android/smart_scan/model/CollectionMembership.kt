package thesis.android.smart_scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class CollectionMembership(
    @Id
    var id: Long = 0,
    @Index
    var collectionId: Long = 0,
    @Index
    var imageId: Long = 0,
    @Unique
    var membershipKey: String = "",
    var addedAt: Long = System.currentTimeMillis()
)
