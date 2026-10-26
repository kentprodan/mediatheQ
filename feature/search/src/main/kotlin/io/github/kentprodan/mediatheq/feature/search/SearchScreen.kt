package io.github.kentprodan.mediatheq.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.ui.AlbumRow
import io.github.kentprodan.mediatheq.core.ui.ArtistRow
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.PlaylistRow
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.TrackRow

@Composable
internal fun SearchRoute(
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(state, viewModel::onAction, onOpenAlbum, onOpenArtist, onOpenPlaylist)
}

/**
 * Stateless Search tab: the search field on top, and below it the recent searches or the results grouped by kind. The
 * field's text lives in [textFieldState] (saved across process death) and reaches the ViewModel as
 * [SearchAction.QueryChanged].
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState = rememberTextFieldState(),
) {
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { onAction(SearchAction.QueryChanged(it)) }
    }
    Surface(modifier.fillMaxSize()) {
        SearchContent(state, onAction, onOpenAlbum, onOpenArtist, onOpenPlaylist, textFieldState)
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
    textFieldState: TextFieldState,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchField(textFieldState, onSearch = { onAction(SearchAction.Submit) })
        val open = { navigate: (MediaId) -> Unit ->
            { id: MediaId ->
                onAction(SearchAction.ResultOpened)
                navigate(id)
            }
        }
        when (state) {
            SearchUiState.Loading -> {
                // A local read of a few milliseconds: nothing to show meanwhile.
                Box(Modifier.fillMaxSize())
            }

            is SearchUiState.Recent -> {
                RecentSearches(
                    state.searches,
                    onSelect = { textFieldState.setTextAndPlaceCursorAtEnd(it) },
                    onClear = { onAction(SearchAction.ClearRecent) },
                )
            }

            is SearchUiState.Results -> {
                Results(state.results, onAction, open(onOpenAlbum), open(onOpenArtist), open(onOpenPlaylist))
            }

            is SearchUiState.NoResults -> {
                MqEmptyState(
                    MqIcons.Search,
                    stringResource(R.string.search_no_results_title),
                    body =
                        stringResource(
                            if (state.offline) R.string.search_no_downloads_body else R.string.search_no_results_body,
                            state.query,
                        ),
                )
            }

            is SearchUiState.Error -> {
                MqEmptyState(
                    MqIcons.Search,
                    stringResource(R.string.search_error_title),
                    body = stringResource(R.string.search_error_body),
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = { onAction(SearchAction.Retry) },
                )
            }
        }
    }
}

/**
 * The M3 search input field on a search bar surface. Not the collapsed `SearchBar`: that one disables the soft keyboard
 * and expects an expanded bar to show results, while here the results stay on the page below the field.
 */
@Composable
private fun SearchField(
    textFieldState: TextFieldState,
    onSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
    ) {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = rememberSearchBarState(),
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(MqIcons.Search, contentDescription = null) },
            trailingIcon =
                if (textFieldState.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { textFieldState.clearText() }) {
                            Icon(MqIcons.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun RecentSearches(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (searches.isEmpty()) {
        MqEmptyState(
            MqIcons.Search,
            stringResource(R.string.search_start_title),
            body = stringResource(R.string.search_start_body),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        item(key = "recent-title") {
            Row(
                Modifier.fillMaxWidth().padding(start = MediatheqSpacing.l, end = MediatheqSpacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_recent),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear_recent)) }
            }
        }
        items(searches, key = { "recent-$it" }) { search ->
            ListItem(
                onClick = { onSelect(search) },
                leadingContent = { Icon(MqIcons.Search, contentDescription = null) },
            ) {
                Text(search, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Results(
    results: SearchResults,
    onAction: (SearchAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenPlaylist: (MediaId) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        section("artists", R.string.search_artists, results.artists, { it.id }) { artist ->
            ArtistRow(artist, onClick = { onOpenArtist(artist.id) })
        }
        section("albums", R.string.search_albums, results.albums, { it.id }) { album ->
            AlbumRow(album, onClick = { onOpenAlbum(album.id) })
        }
        section("songs", R.string.search_songs, results.tracks, { it.id }) { track ->
            TrackRow(
                track,
                onClick = { onAction(SearchAction.PlayTrack(track)) },
                showCover = true,
                menuItems = { close ->
                    QueueMenuItems(
                        onPlayNext = { onAction(SearchAction.PlayNext(track)) },
                        onAddToQueue = { onAction(SearchAction.AddToQueue(track)) },
                        close = close,
                    )
                    FavoriteMenuItem(
                        track.userData.isFavorite,
                        { onAction(SearchAction.SetFavorite(track, it)) },
                        close,
                    )
                    DownloadMenuItem({ onAction(SearchAction.Download(track)) }, close)
                },
            )
        }
        section("playlists", R.string.search_playlists, results.playlists, { it.id }) { playlist ->
            PlaylistRow(playlist, onClick = { onOpenPlaylist(playlist.id) })
        }
    }
}

/** A titled group of results; nothing when [items] is empty. Keys are text so they can be saved in a Bundle. */
private fun <T : Any> LazyListScope.section(
    key: String,
    @StringRes title: Int,
    items: List<T>,
    id: (T) -> MediaId,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key-title") {
        Text(
            stringResource(title),
            Modifier
                .padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.l)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
    items(items, key = { "$key-${id(it)}" }) { row(it) }
}
