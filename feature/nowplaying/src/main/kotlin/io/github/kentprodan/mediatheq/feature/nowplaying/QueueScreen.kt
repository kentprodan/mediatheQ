package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.designsystem.component.MqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.ui.RowCover
import io.github.kentprodan.mediatheq.core.ui.formatDuration

/**
 * The queue in play order, opened from Now Playing: tap an entry to play it, swipe it away to remove it, drag its
 * handle to move it (not while shuffled: the shuffled order is not the queue order). TalkBack users get the same
 * through custom actions on each row.
 */
@Composable
fun QueueScreen(
    queue: List<QueueItem>,
    currentEntryId: String?,
    canReorder: Boolean,
    onAction: (NowPlayingAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A local copy follows the finger while dragging; the player gets one move when it is dropped.
    var items by remember(queue) { mutableStateOf(queue) }
    val listState = rememberLazyListState()
    // Opens at the playing entry, also when the queue arrives a moment after the screen.
    var scrolledToCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(queue, currentEntryId) {
        val current = queue.indexOfFirst { it.entryId == currentEntryId }
        if (!scrolledToCurrent && current >= 0) {
            listState.scrollToItem(current)
            scrolledToCurrent = true
        }
    }
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to -> items = items.toMutableList().apply { add(to, removeAt(from)) } },
            onDrop = { key, to ->
                if (queue.indexOfFirst { it.entryId == key } !=
                    to
                ) {
                    onAction(NowPlayingAction.MoveQueueItem(key as String, to))
                }
            },
        )
    val paneTitle = stringResource(R.string.nowplaying_queue)
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .semantics { this.paneTitle = paneTitle },
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.nowplaying_queue)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.nowplaying_queue_close))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(items, key = { _, item -> item.entryId }) { index, item ->
                QueueEntry(
                    item = item,
                    index = index,
                    count = items.size,
                    isCurrent = item.entryId == currentEntryId,
                    canReorder = canReorder,
                    reorder = reorder,
                    onAction = onAction,
                    // Neighbors glide out of the way; the dragged row follows the finger instead.
                    modifier = if (reorder.draggedKey == item.entryId) Modifier else Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun QueueEntry(
    item: QueueItem,
    index: Int,
    count: Int,
    isCurrent: Boolean,
    canReorder: Boolean,
    reorder: MqReorderState,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier,
) {
    val swipe = rememberSwipeToDismissBoxState()
    val remove = NowPlayingAction.RemoveQueueItem(item.entryId)
    val nowPlaying = stringResource(R.string.nowplaying_pane_title)
    val removeLabel = stringResource(R.string.nowplaying_queue_remove)
    val moveUpLabel = stringResource(R.string.nowplaying_queue_move_up)
    val moveDownLabel = stringResource(R.string.nowplaying_queue_move_down)
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.mqReorderItem(reorder, item.entryId),
        // Only while swiping: the rows are rounded, and a resting background would show at their corners.
        backgroundContent = { if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) RemoveBackground() },
        onDismiss = { onAction(remove) },
    ) {
        ListItem(
            onClick = { onAction(NowPlayingAction.SkipToQueueItem(item.entryId)) },
            modifier =
                Modifier.semantics {
                    if (isCurrent) stateDescription = nowPlaying
                    customActions =
                        buildList {
                            if (canReorder && index > 0) {
                                add(
                                    CustomAccessibilityAction(moveUpLabel) {
                                        onAction(NowPlayingAction.MoveQueueItem(item.entryId, index - 1))
                                        true
                                    },
                                )
                            }
                            if (canReorder && index < count - 1) {
                                add(
                                    CustomAccessibilityAction(moveDownLabel) {
                                        onAction(NowPlayingAction.MoveQueueItem(item.entryId, index + 1))
                                        true
                                    },
                                )
                            }
                            add(
                                CustomAccessibilityAction(removeLabel) {
                                    onAction(remove)
                                    true
                                },
                            )
                        }
                },
            leadingContent = {
                // The playing entry shows the equalizer in place of its cover (as on the album page), same size.
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.alpha(if (isCurrent) 0f else 1f)) { RowCover(item.artwork, MqIcons.MusicNote) }
                    if (isCurrent) {
                        Icon(MqIcons.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            supportingContent = { Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(item.duration), style = MaterialTheme.typography.bodySmall)
                    if (canReorder) {
                        // Not a button: the drag is the gesture, the row's custom actions are the TalkBack way.
                        Icon(
                            MqIcons.DragHandle,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .mqReorderHandle(reorder, item.entryId),
                        )
                    }
                }
            },
        ) {
            Text(
                item.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shown under a row while it is swiped away. */
@Composable
private fun RemoveBackground() {
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = MediatheqSpacing.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MqIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Icon(MqIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}
