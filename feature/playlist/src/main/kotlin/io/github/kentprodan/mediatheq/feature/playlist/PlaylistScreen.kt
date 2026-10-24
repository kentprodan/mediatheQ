package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayShuffleButtons
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount

@Composable
internal fun PlaylistRoute(
    playlistId: MediaId,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel =
        hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(key = playlistId.toString(), creationCallback = {
            it.create(playlistId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == PlaylistUiState.Deleted) onBack() }
    PlaylistScreen(state, viewModel::onAction, onBack)
}

/**
 * Stateless playlist page: collapsing top bar, cover, Play / Shuffle, tracks with drag handles. A long press starts
 * selecting; the floating toolbar then removes the selection. Rename and delete are in the top bar's menu.
 */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onAction: (PlaylistAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val content = state as? PlaylistUiState.Content
    var dialog by rememberSaveable { mutableStateOf<PlaylistDialog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val editFailed = stringResource(R.string.playlist_edit_failed)
    LaunchedEffect(content?.error) {
        if (content?.error != null) {
            snackbarHostState.showSnackbar(editFailed)
            onAction(PlaylistAction.DismissError)
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(content?.playlist?.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    if (content != null) {
                        Text(
                            listOfNotNull(
                                formatTrackCount(content.items.size),
                                formatTotalDuration(content.duration).takeIf { content.items.isNotEmpty() },
                            ).joinToString(" · "),
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.playlist_back))
                    }
                },
                actions = {
                    if (content != null) {
                        OverflowMenu(stringResource(R.string.playlist_more_options, content.playlist.name)) { close ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_rename)) },
                                leadingIcon = { Icon(MqIcons.Edit, contentDescription = null) },
                                onClick = {
                                    dialog = PlaylistDialog.Rename
                                    close()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_delete)) },
                                leadingIcon = { Icon(MqIcons.Delete, contentDescription = null) },
                                onClick = {
                                    dialog = PlaylistDialog.Delete
                                    close()
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when (state) {
            PlaylistUiState.Loading, PlaylistUiState.Deleted -> {
                Unit
            }

            PlaylistUiState.NotFound -> {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.playlist_not_found_title),
                    Modifier.padding(padding),
                    body = stringResource(R.string.playlist_not_found_body),
                    actionLabel = stringResource(R.string.playlist_back),
                    onAction = onBack,
                )
            }

            is PlaylistUiState.Content -> {
                Box(Modifier.fillMaxSize()) {
                    // While selecting, the last rows can scroll up from under the floating toolbar.
                    val toolbarSpace =
                        if (state.isSelecting) {
                            FloatingToolbarDefaults.ContainerSize + FloatingToolbarDefaults.ScreenOffset * 2
                        } else {
                            MediatheqSpacing.l
                        }
                    PlaylistList(
                        state,
                        onAction,
                        PaddingValues(top = padding.calculateTopPadding(), bottom = toolbarSpace),
                    )
                    if (state.isSelecting) {
                        SelectionToolbar(state, onAction, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }
    if (content != null) {
        when (dialog) {
            PlaylistDialog.Rename -> {
                RenameDialog(
                    content.playlist.name,
                    onRename = {
                        onAction(PlaylistAction.Rename(it))
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            PlaylistDialog.Delete -> {
                DeleteDialog(
                    content.playlist.name,
                    onDelete = {
                        onAction(PlaylistAction.Delete)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            null -> {
                Unit
            }
        }
    }
}

internal enum class PlaylistDialog { Rename, Delete }

@Composable
private fun PlaylistList(
    state: PlaylistUiState.Content,
    onAction: (PlaylistAction) -> Unit,
    contentPadding: PaddingValues,
) {
    // A local copy follows the finger while dragging; the repository gets one move when it is dropped.
    var rows by remember(state.items) { mutableStateOf(state.items) }
    val listState = rememberLazyListState()
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to ->
                rows = rows.toMutableList().apply { add(to - HEADER_ITEMS, removeAt(from - HEADER_ITEMS)) }
            },
            onDrop = { key, to ->
                // The drop index names the item that was there before the drag: it is the move's target.
                val target = state.items.getOrNull(to - HEADER_ITEMS)
                if (target != null && target.position != key) onAction(PlaylistAction.Move(key as Int, target.position))
            },
            canDropOn = { it is Int },
        )
    val canReorder = state.canEdit && !state.isSelecting
    LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = contentPadding) {
        item(key = "header") { PlaylistHeader(state) }
        item(key = "buttons") {
            MqPlayShuffleButtons(
                enabled = state.items.isNotEmpty(),
                onPlay = { onAction(PlaylistAction.Play) },
                onShuffle = { onAction(PlaylistAction.Shuffle) },
            )
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                MqEmptyState(
                    MqIcons.MusicNote,
                    stringResource(R.string.playlist_empty_title),
                    body = stringResource(R.string.playlist_empty_body),
                )
            }
        }
        items(rows, key = { it.position }) { item ->
            PlaylistRow(
                item,
                state,
                rows,
                canReorder,
                onAction,
                Modifier
                    .mqReorderItem(reorder, item.position)
                    .then(if (reorder.draggedKey == item.position) Modifier else Modifier.animateItem()),
                handle = Modifier.mqReorderHandle(reorder, item.position, enabled = canReorder),
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    item: PlaylistItem,
    state: PlaylistUiState.Content,
    rows: List<PlaylistItem>,
    canReorder: Boolean,
    onAction: (PlaylistAction) -> Unit,
    modifier: Modifier,
    handle: Modifier,
) {
    val index = rows.indexOf(item)
    val moveUp = stringResource(R.string.playlist_move_up)
    val moveDown = stringResource(R.string.playlist_move_down)
    val remove = stringResource(R.string.playlist_remove)
    val canEditItem = state.canEdit && !item.isPending
    Row(
        modifier.semantics {
            customActions =
                buildList {
                    if (canReorder && index > 0) {
                        add(
                            CustomAccessibilityAction(moveUp) {
                                onAction(PlaylistAction.Move(item.position, rows[index - 1].position))
                                true
                            },
                        )
                    }
                    if (canReorder && index < rows.size - 1) {
                        add(
                            CustomAccessibilityAction(moveDown) {
                                onAction(PlaylistAction.Move(item.position, rows[index + 1].position))
                                true
                            },
                        )
                    }
                }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackRow(
            item.track,
            onClick = {
                onAction(
                    if (state.isSelecting) {
                        PlaylistAction.ToggleSelection(item.position)
                    } else {
                        PlaylistAction.PlayItem(item.position)
                    },
                )
            },
            modifier = Modifier.weight(1f),
            showCover = true,
            isCurrent = item.track.id == state.currentTrackId,
            onLongClick = { if (canEditItem) onAction(PlaylistAction.ToggleSelection(item.position)) },
            selected = if (state.isSelecting) item.entryId in state.selected else null,
            menuItems =
                if (state.isSelecting) {
                    null
                } else {
                    { close ->
                        QueueMenuItems(
                            onPlayNext = { onAction(PlaylistAction.PlayNext(item.position)) },
                            onAddToQueue = { onAction(PlaylistAction.AddToQueue(item.position)) },
                            close = close,
                        )
                        FavoriteMenuItem(
                            item.track.userData.isFavorite,
                            { onAction(PlaylistAction.SetFavorite(item.position, it)) },
                            close,
                        )
                        if (canEditItem) {
                            DropdownMenuItem(
                                text = { Text(remove) },
                                leadingIcon = { Icon(MqIcons.Delete, contentDescription = null) },
                                onClick = {
                                    onAction(PlaylistAction.Remove(item.position))
                                    close()
                                },
                            )
                        }
                    }
                },
        )
        if (canReorder) {
            // Not a button: the drag is the gesture, the row's custom actions are the TalkBack way.
            Icon(
                MqIcons.DragHandle,
                contentDescription = null,
                modifier = Modifier.minimumInteractiveComponentSize().then(handle),
            )
        }
    }
}

@Composable
private fun PlaylistHeader(state: PlaylistUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MqCover(
            model = state.playlist.artwork,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(COVER_WIDTH_FRACTION),
            blurHash = state.playlist.artwork?.blurHash,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun SelectionToolbar(
    state: PlaylistUiState.Content,
    onAction: (PlaylistAction) -> Unit,
    modifier: Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.padding(FloatingToolbarDefaults.ScreenOffset),
    ) {
        IconButton(onClick = { onAction(PlaylistAction.ClearSelection) }) {
            Icon(MqIcons.Close, contentDescription = stringResource(R.string.playlist_clear_selection))
        }
        Text(
            pluralStringResource(R.plurals.playlist_selected, state.selected.size, state.selected.size),
            Modifier.align(Alignment.CenterVertically).padding(horizontal = MediatheqSpacing.s),
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = { onAction(PlaylistAction.RemoveSelected) }, enabled = state.canEdit) {
            Icon(MqIcons.Delete, contentDescription = stringResource(R.string.playlist_remove))
        }
    }
}

@Composable
private fun RenameDialog(
    name: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = rememberTextFieldState(name)
    MqDialog(
        title = stringResource(R.string.playlist_rename),
        confirmLabel = stringResource(R.string.playlist_save),
        onConfirm = { onRename(field.text.toString()) },
        dismissLabel = stringResource(R.string.playlist_cancel),
        onDismiss = onDismiss,
        confirmEnabled = field.text.isNotBlank(),
    ) {
        OutlinedTextField(field, label = {
            Text(stringResource(R.string.playlist_name))
        }, lineLimits = TextFieldLineLimits.SingleLine)
    }
}

@Composable
private fun DeleteDialog(
    name: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = stringResource(R.string.playlist_delete_title, name),
        confirmLabel = stringResource(R.string.playlist_delete_confirm),
        onConfirm = onDelete,
        dismissLabel = stringResource(R.string.playlist_cancel),
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.playlist_delete_body), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val HEADER_ITEMS = 2
private const val COVER_WIDTH_FRACTION = 0.6f
