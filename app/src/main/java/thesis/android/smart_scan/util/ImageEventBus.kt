package thesis.android.smart_scan.util

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NewImageEvent(
    val uri: Uri,
    val processingStartedAtMs: Long
)

object ImageEventBus {
    private val _newImageFlow = MutableSharedFlow<NewImageEvent>(extraBufferCapacity = 16)
    val newImageFlow: SharedFlow<NewImageEvent> = _newImageFlow.asSharedFlow()

    fun notifyNewImage(uri: Uri, processingStartedAtMs: Long) {
        _newImageFlow.tryEmit(NewImageEvent(uri, processingStartedAtMs))
    }
}
