package thesis.android.smart_scan.service.mlkit.speech_to_text

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SpeechRecognitionDelegate(
    val activity: FragmentActivity
) {
    private var pending: CancellableContinuation<ActivityResult>? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cont = pending
        pending = null
        cont?.resume(result)
    }

    suspend fun startForResult(intent: Intent): ActivityResult =
        suspendCancellableCoroutine { cont ->
            if (pending != null) {
                cont.resumeWithException(IllegalStateException("Đang có phiên nhận dạng giọng nói khác"))
                return@suspendCancellableCoroutine
            }
            pending = cont
            try {
                launcher.launch(intent)
            } catch (e: Throwable) {
                pending = null
                cont.resumeWithException(e as? Exception ?: RuntimeException(e))
            }
            cont.invokeOnCancellation {
                if (pending === cont) pending = null
            }
        }
}
