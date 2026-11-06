package io.github.kentprodan.mediatheq.feature.playlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayShuffleButtons
import io.github.kentprodan.mediatheq.core.designsystem.component.bottomBarsPadding
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.LocalMqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.imageColorScheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColors
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import io.github.kentprodan.mediatheq.core.ui.SelectionToolbar
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount
import io.github.kentprodan.mediatheq.core.ui.trackSwipes

@Composable
internal fun PlaylistRoute(
    playlistId: MediaId,
    onBack: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    viewModel: PlaylistViewModel =
        hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(key = playlistId.toString(), creationCallback = {
            it.create(playlistId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == PlaylistUiState.Deleted) onBack() }
    PlaylistScreen(state, viewModel::onAction, onBack, onAddToPlaylist = onAddToPlaylist)
}

/**
 * Stateless playlist page: the header (#135: image, name, counts, buttons; rename, delete, download and edit in More),
 * then the tracks with drag handles. A long press (or More -> Edit) starts selecting; the floating toolbar then acts on
 * the selection. With dynamic colors on the page takes the playlist image's scheme.
 */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onAction: (PlaylistAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
    /** Screenshots: these colors instead of the image's (previews load no images). */
    imageColors: CoverColors? = null,
) {
    val content = state as? PlaylistUiState.Content
    PlaylistColors(content, imageColors) { imageColor ->
        PlaylistPage(state, content, imageColor, onAction, onBack, modifier, onAddToPlaylist)
    }
}

@Composable
private fun PlaylistPage(
    state: PlaylistUiState,
    content: PlaylistUiState.Content?,
    imageColor: Color?,
    onAction: (PlaylistAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val swipe =
        trackSwipes(
            onAddToPlaylist,
            onRate = { track, stars -> onAction(PlaylistAction.RateTrack(track, stars)) },
            onCommand = { track, right -> onAction(PlaylistAction.SwipeTrack(track, right)) },
        )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val drawsUnderBar = content != null && content.page.headerBackground != PlaylistHeaderBackground.None
    var dialog by rememberSaveable { mutableStateOf<PlaylistDialog?>(null) }
    BackHandler(enabled = content?.isSelecting == true) { onAction(PlaylistAction.ClearSelection) }
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
                // The header shows the name; the bar names the playlist only once it has nearly collapsed.
                title = {
                    Text(
                        content?.playlist?.name.orEmpty(),
                        Modifier.graphicsLayer { alpha = titleAlpha(scrollBehavior.state.collapsedFraction) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.playlist_back))
                    }
                },
                colors =
                    if (drawsUnderBar) {
                        TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    } else {
                        TopAppBarDefaults.topAppBarColors()
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
                    val barHeight = padding.calculateTopPadding()
                    PlaylistList(
                        state,
                        onAction,
                        PaddingValues(top = if (drawsUnderBar) 0.dp else barHeight, bottom = toolbarSpace)
                            .plusBottomBars(),
                        swipe,
                        header = {
                            PlaylistHeader(
                                state,
                                topPadding = if (drawsUnderBar) barHeight else 0.dp,
                                imageColor = imageColor,
                                onAction = onAction,
                                onDialog = { dialog = it },
                            )
                        },
                    )
                    if (state.isSelecting) {
                        PlaylistSelectionToolbar(
                            state,
                            onAction,
                            onAddToPlaylist,
                            Modifier.align(Alignment.BottomCenter).bottomBarsPadding(),
                        )
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
    onSwipe: (Track, Boolean) -> Unit,
    header: @Composable () -> Unit,
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
        item(key = "header") { header() }
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
                onSwipe = onSwipe,
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
    onSwipe: (Track, Boolean) -> Unit,
) {
    val index = rows.indexOf(item)
    val moveUp = stringResource(R.string.playlist_move_up)
    val moveDown = stringResource(R.string.playlist_move_down)
    val remove = stringResource(R.string.playlist_remove)
    val canEditItem = state.canEdit && !item.isPending
    val haptics = LocalHapticFeedback.current
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
            // Any playlist can select (#123); only an editable one can then remove.
            onLongClick = {
                if (!item.isPending) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction(PlaylistAction.ToggleSelection(item.position))
                }
            },
            selected = if (state.isSelecting) item.entryId in state.selected else null,
            onSwipe = { onSwipe(item.track, it) },
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
                        DownloadMenuItem({ onAction(PlaylistAction.Download(item.position)) }, close)
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

/** The shared selection toolbar (#123); Remove joins it while the playlist can be edited. */
@Composable
private fun PlaylistSelectionToolbar(
    state: PlaylistUiState.Content,
    onAction: (PlaylistAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    modifier: Modifier,
) {
    SelectionToolbar(
        state.selected.size,
        onCommand = { command ->
            if (command == SelectionCommand.AddToPlaylist) {
                onAddToPlaylist(state.items.filter { it.entryId in state.selected }.map { it.track.id })
                onAction(PlaylistAction.ClearSelection)
            } else {
                onAction(PlaylistAction.OnSelection(command))
            }
        },
        onSelectAll = { onAction(PlaylistAction.SelectAll) },
        onClear = { onAction(PlaylistAction.ClearSelection) },
        modifier = modifier,
    ) {
        if (state.canEdit) {
            IconButton(onClick = { onAction(PlaylistAction.RemoveSelected) }) {
                Icon(MqIcons.Delete, contentDescription = stringResource(R.string.playlist_remove))
            }
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

/** The header, before the rows. */
private const val HEADER_ITEMS = 1

/** The playlist image's colors (ADR 0029): the page's scheme with dynamic colors on, the gradient's color either way. */
@Composable
private fun PlaylistColors(
    content: PlaylistUiState.Content?,
    override: CoverColors?,
    page: @Composable (imageColor: Color?) -> Unit,
) {
    val cache = remember { CoverColorCache() }
    val seed = content?.playlist?.artwork
    val colors = override ?: rememberCoverColors(seed?.let { "${it.itemId}/${it.tag}" }, seed, cache)
    val theme = LocalMqTheme.current
    val dynamic = content?.page?.dynamicColors == true
    val scheme = remember(colors, theme, dynamic) { imageColorScheme(colors, theme).takeIf { dynamic } }
    MqCoverTheme(scheme) { page((scheme ?: colors?.scheme(theme.isDark, vibrant = false))?.primaryContainer) }
}

/** Hidden while the bar is open, fading in over the last part of the collapse. */
private fun titleAlpha(collapsedFraction: Float): Float =
    ((collapsedFraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)

private const val TITLE_FADE_START = 0.7f
