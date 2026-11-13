package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Which item of a screen had focus last, kept with the screen's saved state (ADR 0041): a screen left for a detail page
 * leaves the composition, and `focusRestorer()` alone forgets its child then. Coming back, [restore] focuses that item.
 */
@Stable
class FocusMemory internal constructor(
    private val last: MutableState<String?>,
) {
    internal val requesters = mutableMapOf<String, FocusRequester>()

    /**
     * Focuses the item focused last, else the one with [first] (if given); call once when the screen opens. `false`
     * when there was nothing to focus, so the screen can focus something else (Search's field, #244).
     */
    fun restore(first: String? = null): Boolean {
        val requester = requesters[last.value] ?: first?.let(requesters::get) ?: return false
        // Not attached yet (the item is still being laid out) is not an error: focus stays where it is.
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }

    /** Focuses the item with [key] if it is on screen (the A-Z strip's jump, #237). */
    fun focus(key: String) {
        runCatching { requesters[key]?.requestFocus() }
    }

    internal fun focused(key: String) {
        last.value = key
    }
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val last = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { FocusMemory(last) }
}

/** Remembers this item under [key] in [memory] while it has focus, and lets [FocusMemory.restore] focus it. */
@Composable
fun Modifier.focusMemory(
    memory: FocusMemory,
    key: String,
): Modifier {
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(memory, key) {
        memory.requesters[key] = requester
        onDispose { memory.requesters.remove(key) }
    }
    return focusRequester(requester).onFocusChanged { if (it.isFocused) memory.focused(key) }
}
