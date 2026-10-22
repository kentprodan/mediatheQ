package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Drag to reorder for a `LazyColumn` with keyed items: drag an item by its [mqReorderHandle]; while dragging, [onMove]
 * swaps it with its neighbors (keep a local copy of the list and move there), and [onDrop] gets its key and final
 * index once the finger lifts (commit there, once). Give each item [mqReorderItem] so it follows the finger. Items
 * whose key fails `canDropOn` (e.g. a header above the list) never trade places with the dragged one.
 */
@Stable
class MqReorderState internal constructor(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val onMove: () -> (from: Int, to: Int) -> Unit,
    private val onDrop: () -> (key: Any, to: Int) -> Unit,
    private val canDropOn: () -> (key: Any) -> Boolean,
) {
    /** The key of the item being dragged, or `null`. */
    var draggedKey: Any? by mutableStateOf(null)
        private set

    // Where the dragged item started (in the viewport) and how far the finger moved since.
    private var startOffset = 0
    private var delta by mutableFloatStateOf(0f)

    // The index the last swap moved the item to, until the list has laid it out there.
    private var movedTo = -1

    /** How far the dragged item is drawn from its laid-out place, so it stays under the finger (also while scrolling). */
    internal val offset: Float
        get() = draggedInfo()?.let { startOffset + delta - it.offset } ?: 0f

    private fun draggedInfo(): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }

    internal fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggedKey = key
        startOffset = item.offset
        delta = 0f
        movedTo = -1
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    internal fun drag(amount: Float) {
        val dragged = draggedInfo() ?: return
        delta += amount
        val info = listState.layoutInfo
        val top = startOffset + delta
        val edge = dragged.size / 2f
        // shortcut: the list scrolls only while the finger moves near an edge, not while it rests there; upgrade to a
        // scroll loop if long queues make that tiring.
        val scroll =
            when {
                top + dragged.size > info.viewportEndOffset - edge -> amount.coerceAtLeast(0f)
                top < info.viewportStartOffset + edge -> amount.coerceAtMost(0f)
                else -> 0f
            }
        if (scroll != 0f) listState.dispatchRawDelta(scroll)
        if (movedTo >= 0 && dragged.index != movedTo) return
        val center = top + dragged.size / 2f
        val target =
            info.visibleItemsInfo.firstOrNull {
                it.index != dragged.index && center > it.offset && center < it.offset + it.size && canDropOn()(it.key)
            } ?: return
        movedTo = target.index
        // The list keeps its first visible item in place by key; when that item takes part in the swap it would
        // scroll a whole row. Pinning the current scroll position avoids the jump (as in the Compose drag-and-drop demo).
        val first = listState.firstVisibleItemIndex
        if (dragged.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        onMove()(dragged.index, target.index)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    internal fun end() {
        val key = draggedKey ?: return
        val index = if (movedTo >= 0) movedTo else draggedInfo()?.index
        draggedKey = null
        delta = 0f
        if (index != null) onDrop()(key, index)
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }
}

@Composable
fun rememberMqReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: (key: Any, to: Int) -> Unit,
    canDropOn: (key: Any) -> Boolean = { true },
): MqReorderState {
    val haptics = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val canDrop by rememberUpdatedState(canDropOn)
    return remember(listState, haptics) { MqReorderState(listState, haptics, { move }, { drop }, { canDrop }) }
}

/** Draws the item where the finger is while it is dragged, above its neighbors. */
fun Modifier.mqReorderItem(
    state: MqReorderState,
    key: Any,
): Modifier =
    zIndex(if (state.draggedKey == key) 1f else 0f).graphicsLayer {
        translationY = if (state.draggedKey == key) state.offset else 0f
    }

/** Starts dragging the item with [key] (put it on a handle icon; give the item custom accessibility actions too). */
fun Modifier.mqReorderHandle(
    state: MqReorderState,
    key: Any,
    enabled: Boolean = true,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(state, key) {
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = state::end,
                onDragCancel = state::end,
            )
        }
    }
