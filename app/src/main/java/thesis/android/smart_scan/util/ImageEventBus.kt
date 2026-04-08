package thesis.android.smart_scan.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ImageEventBus {
    private val _newImageFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val newImageFlow: SharedFlow<Unit> = _newImageFlow.asSharedFlow()

    fun notifyNewImage() {
        _newImageFlow.tryEmit(Unit)
    }
}
