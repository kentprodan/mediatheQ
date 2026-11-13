package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.designsystem.tv.FocusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvRowHeader
import io.github.kentprodan.mediatheq.core.ui.tv.title

/** Where a cover leads. */
data class TvHomeNavigation(
    val onOpenAlbum: (MediaId) -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenPlaylist: (MediaId) -> Unit = {},
)

@Composable
fun TvHomeRoute(
    navigation: TvHomeNavigation,
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvHomeScreen(state, viewModel::onAction, navigation)
}

/**
 * Home on TV (#236): a column of rows of covers; in each row the focused card stays 30 % from the left. Select opens an
 * album, artist or playlist, or plays a track or mix; long-press (or the menu key) opens Play, Shuffle, Add to queue.
 */
@Composable
fun TvHomeScreen(
    state: TvHomeUiState,
    onAction: (TvHomeAction) -> Unit,
    navigation: TvHomeNavigation,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TvHomeUiState.Loading -> {
            Box(modifier.fillMaxSize())
        }

        is TvHomeUiState.Syncing -> {
            val progress =
                stringResource(R.string.tv_home_syncing_progress, state.fetched, state.total).takeIf { state.total > 0 }
            Message(
                stringResource(R.string.tv_home_syncing_title),
                stringResource(R.string.tv_home_syncing_body),
                modifier,
                progress,
            )
        }

        TvHomeUiState.Empty -> {
            Message(
                stringResource(R.string.tv_home_empty_title),
                stringResource(R.string.tv_home_empty_body),
                modifier,
            ) {
                onAction(TvHomeAction.Sync)
            }
        }

        is TvHomeUiState.Error -> {
            Message(
                stringResource(R.string.tv_home_error_title),
                stringResource(R.string.tv_home_error_body),
                modifier,
            ) {
                onAction(TvHomeAction.Sync)
            }
        }

        is TvHomeUiState.Content -> {
            Rows(state.rows, onAction, navigation, modifier)
        }
    }
}

/** A cover's menu: what it acts on and the name it shows. */
private data class Menu(
    val target: TvHomeTarget,
    val title: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rows(
    rows: List<TvHomeRow>,
    onAction: (TvHomeAction) -> Unit,
    navigation: TvHomeNavigation,
    modifier: Modifier,
) {
    val focus = rememberFocusMemory()
    var menu by remember { mutableStateOf<Menu?>(null) }
    val cards = Cards(focus, onAction, navigation) { menu = it }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(rows, key = { it.section.name }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MqTvRowHeader(stringResource(row.section.title), Modifier.padding(horizontal = EDGE))
                // Focus coming back into a row lands on the card it left (the column position is kept).
                CompositionLocalProvider(LocalBringIntoViewSpec provides Pivot) {
                    LazyRow(
                        Modifier.fillMaxWidth().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = EDGE, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) { with(cards) { row(row) } }
                }
            }
        }
    }
    // The first card on the first visit, the one focused last when coming back from a detail page.
    LaunchedEffect(Unit) {
        // Lazy rows compose their cards when laid out, after this effect starts: wait for that frame.
        withFrameNanos {}
        rows.firstNotNullOfOrNull(::firstKey)?.let(focus::restore)
    }
    menu?.let { shown -> CoverMenu(shown, onAction) { menu = null } }
}

/** A card's key in the focus memory: by item, so a row that changes while a detail page is open still finds it. */
private fun focusKey(
    row: TvHomeRow,
    item: Any,
): String = "${row.section.name}/$item"

private fun firstKey(row: TvHomeRow): String? =
    when (val items = row.items) {
        is HomeItems.Albums -> items.albums.firstOrNull()?.id
        is HomeItems.Artists -> items.artists.firstOrNull()?.id
        is HomeItems.Playlists -> items.playlists.firstOrNull()?.id
        is HomeItems.Tracks -> items.tracks.firstOrNull()?.id
        is HomeItems.Shortcuts -> Mix.Tracks
    }?.let { focusKey(row, it) }

/** The cards of one row, by what the section holds. */
private class Cards(
    val focus: FocusMemory,
    val onAction: (TvHomeAction) -> Unit,
    val navigation: TvHomeNavigation,
    val openMenu: (Menu) -> Unit,
) {
    fun LazyListScope.row(row: TvHomeRow) {
        when (val items = row.items) {
            is HomeItems.Albums -> {
                itemsIndexed(items.albums, key = { _, it -> it.id.toString() }) { index, album ->
                    Card(
                        focusKey(row, album.id),
                        album.title,
                        album.artwork,
                        names(album.albumArtists),
                        Menu(TvHomeTarget.Album(album.id), album.title),
                    ) { navigation.onOpenAlbum(album.id) }
                }
            }

            is HomeItems.Artists -> {
                itemsIndexed(items.artists, key = { _, it -> it.id.toString() }) { index, artist ->
                    Card(
                        focusKey(row, artist.id),
                        artist.name,
                        artist.artwork,
                        null,
                        Menu(TvHomeTarget.Artist(artist.id), artist.name),
                        MqTvIcons.Person,
                    ) { navigation.onOpenArtist(artist.id) }
                }
            }

            is HomeItems.Playlists -> {
                itemsIndexed(items.playlists, key = { _, it -> it.id.toString() }) { index, playlist ->
                    Card(
                        focusKey(row, playlist.id),
                        playlist.name,
                        playlist.artwork,
                        null,
                        Menu(TvHomeTarget.Playlist(playlist.id), playlist.name),
                    ) { navigation.onOpenPlaylist(playlist.id) }
                }
            }

            is HomeItems.Tracks -> {
                itemsIndexed(items.tracks, key = { _, it -> it.id.toString() }) { index, track ->
                    Card(
                        focusKey(row, track.id),
                        track.title,
                        track.artwork,
                        names(track.artists),
                        Menu(TvHomeTarget.Song(track), track.title),
                    ) { onAction(TvHomeAction.PlayTracks(items.tracks, index)) }
                }
            }

            is HomeItems.Shortcuts -> {
                val mixes = listOf(Mix.Tracks, Mix.Albums) + items.decades.map(Mix::Decade)
                itemsIndexed(mixes, key = { _, it -> it.toString() }) { index, mix ->
                    val (title, icon) =
                        when (mix) {
                            Mix.Tracks -> {
                                stringResource(R.string.tv_home_track_mix) to MqTvIcons.Shuffle
                            }

                            Mix.Albums -> {
                                stringResource(R.string.tv_home_album_mix) to MqTvIcons.Library
                            }

                            is Mix.Decade -> {
                                stringResource(R.string.tv_home_decade_mix, mix.startYear) to
                                    MqTvIcons.Radio
                            }
                        }
                    Card(focusKey(row, mix), title, null, null, null, icon) { onAction(TvHomeAction.PlayMix(mix)) }
                }
            }
        }
    }

    @Composable
    private fun Card(
        key: String,
        title: String,
        artwork: Artwork?,
        subtitle: String?,
        menu: Menu?,
        placeholder: ImageVector = MqTvIcons.MusicNote,
        onClick: () -> Unit,
    ) {
        val open = menu?.let { { openMenu(it) } }
        MqTvAlbumCard(
            title,
            artwork,
            onClick,
            Modifier
                .focusMemory(focus, key)
                .onPreviewKeyEvent { event ->
                    // The remote's menu key does what a long-press does.
                    val isMenu = event.key == Key.Menu && open != null
                    if (isMenu && event.type == KeyEventType.KeyUp) open?.invoke()
                    isMenu
                },
            subtitle = subtitle,
            placeholder = placeholder,
            onLongClick = open,
        )
    }

    private fun names(artists: List<Artist>): String? = artists.joinToString { it.name }.ifEmpty { null }
}

@Composable
private fun CoverMenu(
    menu: Menu,
    onAction: (TvHomeAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // A long-press opens this while select is still held: its repeats and its release would press Play. Select counts
    // only after a fresh press in the menu.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .width(360.dp)
                    .padding(16.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.key !in SELECT_KEYS) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                        !armed
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    menu.title,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                MqTvListItem(
                    stringResource(R.string.tv_home_play),
                    {
                        onAction(TvHomeAction.Play(menu.target))
                        onDismiss()
                    },
                    Modifier.focusRequester(first),
                    icon = MqTvIcons.Play,
                )
                MqTvListItem(
                    stringResource(R.string.tv_home_shuffle),
                    {
                        onAction(TvHomeAction.Play(menu.target, shuffle = true))
                        onDismiss()
                    },
                    icon = MqTvIcons.Shuffle,
                )
                MqTvListItem(
                    stringResource(R.string.tv_home_add_to_queue),
                    {
                        onAction(TvHomeAction.AddToQueue(menu.target))
                        onDismiss()
                    },
                    icon = MqTvIcons.Add,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A state without rows: a title, a line, the sync progress or a Sync now button. */
@Composable
private fun Message(
    title: String,
    body: String,
    modifier: Modifier,
    progress: String? = null,
    onSync: (() -> Unit)? = null,
) = MqTvMessage(
    title,
    body,
    modifier,
    progress,
    onSync?.let { stringResource(R.string.tv_home_sync_now) },
    onSync ?: {},
)

private val EDGE = 48.dp

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** The focused card sits 30 % from the row's start; rows scroll under that fixed line (#236). */
@OptIn(ExperimentalFoundationApi::class)
private val Pivot =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = offset - PIVOT * containerSize
    }

private const val PIVOT = 0.3f
