package io.github.kentprodan.mediatheq.feature.tv.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.tv.FocusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvTextField
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvRowHeader

/** Where a selected result goes. */
data class TvSearchNavigation(
    val onOpenAlbum: (MediaId) -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenPlaylist: (MediaId) -> Unit = {},
)

@Composable
fun TvSearchRoute(
    navigation: TvSearchNavigation,
    viewModel: TvSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSearchScreen(query, state, viewModel::onAction, navigation)
}

/**
 * Search on TV (#244): the field at the top (the system TV keyboard types into it), under it the recent searches or
 * the result rows (Albums, Artists, Tracks, Playlists). Down from the field enters them; Back from them returns to the
 * field, Back on the field leaves Search. Coming back from a page focuses the result that opened it, else the field.
 */
@Composable
fun TvSearchScreen(
    query: String,
    state: TvSearchUiState,
    onAction: (TvSearchAction) -> Unit,
    navigation: TvSearchNavigation,
) {
    val field = remember { FocusRequester() }
    val focus = rememberFocusMemory()
    var inResults by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MqTvTextField(
                query,
                { onAction(TvSearchAction.QueryChanged(it)) },
                stringResource(R.string.tv_search_label),
                Modifier
                    .padding(horizontal = EDGE)
                    .width(560.dp)
                    .focusRequester(field)
                    // Down leaves the field for the results, whatever the key's source (a text field may keep it).
                    .onPreviewKeyEvent { event ->
                        event.key == Key.DirectionDown &&
                            event.type == KeyEventType.KeyDown &&
                            focusManager.moveFocus(FocusDirection.Down)
                    },
                imeAction = ImeAction.Search,
                onDone = {
                    onAction(TvSearchAction.Submit)
                    // The Search key has no default action: the keyboard would stay and keep the D-pad.
                    keyboard?.hide()
                },
            )
            val below = Modifier.fillMaxSize().onFocusChanged { inResults = it.hasFocus }
            when (state) {
                TvSearchUiState.Loading -> {
                    Unit
                }

                is TvSearchUiState.Recent -> {
                    Recent(state.searches, below, focus) {
                        onAction(TvSearchAction.PickRecent(it))
                        // The list gives way to the results: focus waits in the field, Down enters them.
                        runCatching { field.requestFocus() }
                    }
                }

                is TvSearchUiState.Results -> {
                    Rows(state, below, focus, onAction, navigation)
                }

                TvSearchUiState.NoResults -> {
                    Message(stringResource(R.string.tv_search_no_results, query.trim()))
                }

                is TvSearchUiState.Error -> {
                    Message(
                        stringResource(
                            if (state.error ==
                                MqError.Storage
                            ) {
                                R.string.tv_search_storage
                            } else {
                                R.string.tv_search_error
                            },
                        ),
                    )
                }
            }
        }
    }
    // Only while focus is in the results: on the field (or in the drawer) Back goes on to the shell.
    BackHandler(enabled = inResults) { runCatching { field.requestFocus() } }
    LaunchedEffect(Unit) {
        // The results are lazy items, laid out after this effect starts.
        withFrameNanos {}
        if (!focus.restore()) runCatching { field.requestFocus() }
    }
}

@Composable
private fun Recent(
    searches: List<String>,
    modifier: Modifier,
    focus: FocusMemory,
    onPick: (String) -> Unit,
) {
    if (searches.isEmpty()) {
        Message(stringResource(R.string.tv_search_hint))
        return
    }
    LazyColumn(
        modifier.width(720.dp),
        contentPadding = PaddingValues(horizontal = EDGE, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { MqTvRowHeader(stringResource(R.string.tv_search_recent), Modifier.padding(bottom = 8.dp)) }
        items(searches, key = { it }) { search ->
            MqTvListItem(
                search,
                { onPick(search) },
                Modifier.focusMemory(focus, "recent/$search"),
                icon = MqTvIcons.Search,
            )
        }
    }
}

@Composable
private fun Rows(
    state: TvSearchUiState.Results,
    modifier: Modifier,
    focus: FocusMemory,
    onAction: (TvSearchAction) -> Unit,
    navigation: TvSearchNavigation,
) {
    fun opened(open: () -> Unit) {
        onAction(TvSearchAction.Opened)
        open()
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        row(R.string.tv_search_albums, state.albums.isNotEmpty()) {
            items(state.albums, key = { it.id.toString() }) { album ->
                Card("album/${album.id}", album.title, album.artwork, names(album.albumArtists), focus) {
                    opened { navigation.onOpenAlbum(album.id) }
                }
            }
        }
        row(R.string.tv_search_artists, state.artists.isNotEmpty()) {
            items(state.artists, key = { it.id.toString() }) { artist ->
                Card("artist/${artist.id}", artist.name, artist.artwork, null, focus, MqTvIcons.Person) {
                    opened { navigation.onOpenArtist(artist.id) }
                }
            }
        }
        row(R.string.tv_search_tracks, state.tracks.isNotEmpty()) {
            itemsIndexed(state.tracks, key = { _, it -> it.id.toString() }) { index, track ->
                Card("track/${track.id}", track.title, track.artwork, names(track.artists), focus) {
                    onAction(TvSearchAction.PlayTrack(index))
                }
            }
        }
        row(R.string.tv_search_playlists, state.playlists.isNotEmpty()) {
            items(state.playlists, key = { it.id.toString() }) { playlist ->
                Card("playlist/${playlist.id}", playlist.name, playlist.artwork, null, focus) {
                    opened { navigation.onOpenPlaylist(playlist.id) }
                }
            }
        }
    }
}

/** One row of results: its title over the cards; nothing when it [shows] none. */
private fun LazyListScope.row(
    title: Int,
    shows: Boolean,
    cards: LazyListScope.() -> Unit,
) {
    if (!shows) return
    item(key = title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MqTvRowHeader(stringResource(title), Modifier.padding(horizontal = EDGE))
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = EDGE, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                content = cards,
            )
        }
    }
}

@Composable
private fun Card(
    key: String,
    title: String,
    artwork: Artwork?,
    subtitle: String?,
    focus: FocusMemory,
    placeholder: ImageVector = MqTvIcons.MusicNote,
    onClick: () -> Unit,
) = MqTvAlbumCard(
    title,
    artwork,
    onClick,
    Modifier.focusMemory(focus, key),
    subtitle = subtitle,
    placeholder = placeholder,
)

@Composable
private fun Message(text: String) =
    Text(
        text,
        Modifier.padding(horizontal = EDGE, vertical = 16.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

private fun names(artists: List<Artist>): String? = artists.joinToString { it.name }.ifEmpty { null }

private val EDGE = 48.dp
