package app.camplanner.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A line for the snackbar, optionally with Undo. */
data class Notice(val text: String, val undo: (suspend () -> Unit)? = null)

/**
 * App-wide snackbar queue. Screens post here after deleting or changing something; the root
 * scaffold shows it and runs Undo if tapped.
 */
object Notices {
    private val flow = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    val notices: SharedFlow<Notice> = flow

    fun show(text: String, undo: (suspend () -> Unit)? = null) {
        flow.tryEmit(Notice(text, undo))
    }
}
