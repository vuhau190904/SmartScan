package thesis.android.smart_scan.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Reminder(
    @Id
    var id: Long = 0,
    @Index
    var imageId: Long = 0,
    var title: String = "",
    var note: String? = null,
    @Index
    var reminderTime: Long = 0,
    var createdAt: Long = System.currentTimeMillis()
)