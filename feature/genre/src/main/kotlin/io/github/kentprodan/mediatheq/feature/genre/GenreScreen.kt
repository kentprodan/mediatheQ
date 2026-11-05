package io.github.kentprodan.mediatheq.feature.genre

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayShuffleButtons
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.ArtistCard
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.genreCounts

@Composable
internal fun GenreRoute(
    genreId: MediaId,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onBack: () -> Unit,
    viewModel: GenreViewModel =
        hiltViewModel<GenreViewModel, GenreViewModel.Factory>(key = genreId.toString(), creationCallback = {
            it.create(genreId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenreScreen(state, viewModel::onAction, onOpenAlbum, onOpenArtist, onBack)
}

/**
 * Stateless genre page (#130): the genre's name and counts, Play / Shuffle on its tracks, then its albums (grid), album
 * artists (row) and tracks (the first ten, then "Show all").
 */
@Composable
fun GenreScreen(
    state: GenreUiState,
    onAction: (GenreAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val content = state as? GenreUiState.Content
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(content?.genre?.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.genre_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            state == GenreUiState.Loading -> {
                Unit
            }

            content == null -> {
                MqEmptyState(
                    MqIcons.Genres,
                    stringResource(R.string.genre_not_found_title),
                    Modifier.padding(padding),
                    body = stringResource(R.string.genre_not_found_body),
                    actionLabel = stringResource(R.string.genre_back),
                    onAction = onBack,
                )
            }

            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()).plusBottomBars(),
                ) {
                    item(key = "header") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l),
                            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                        ) {
                            Text(
                                genreCounts(content.genre),
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    item(key = "buttons") {
                        MqPlayShuffleButtons(
                            enabled = content.tracks.isNotEmpty(),
                            onPlay = { onAction(GenreAction.Play) },
                            onShuffle = { onAction(GenreAction.Shuffle) },
                        )
                    }
                    albumGrid(content, onOpenAlbum)
                    if (content.artists.isNotEmpty()) {
                        item(key = "artists-title") { SectionTitle(R.string.genre_album_artists) }
                        item(key = "artists") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
                                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                            ) {
                                items(content.artists, key = { it.id.toString() }) { artist ->
                                    ArtistCard(
                                        artist,
                                        { onOpenArtist(artist.id) },
                                        Modifier.width(MediatheqSizes.albumCardInRow),
                                    )
                                }
                            }
                        }
                    }
                    if (content.tracks.isNotEmpty()) {
                        item(key = "tracks-title") { SectionTitle(R.string.genre_tracks) }
                        items(content.shownTracks, key = { "track-${it.id}" }) { track ->
                            TrackRow(
                                track,
                                onClick = { onAction(GenreAction.PlayTrack(track)) },
                                showCover = true,
                                isCurrent = track.id == content.currentTrackId,
                            )
                        }
                        if (content.shownTracks.size < content.tracks.size) {
                            item(key = "show-all") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = { onAction(GenreAction.ShowAllTracks) }) {
                                        Text(stringResource(R.string.genre_show_all))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    @StringRes title: Int,
) {
    Text(
        stringResource(title),
        Modifier
            .padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.l,
                top = MediatheqSpacing.l,
                bottom = MediatheqSpacing.s,
            ).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

/** The albums as rows of the page's list with the Library's column count (a nested lazy grid cannot scroll in it). */
private fun LazyListScope.albumGrid(
    content: GenreUiState.Content,
    onOpenAlbum: (MediaId) -> Unit,
) {
    if (content.albums.isEmpty()) return
    item(key = "albums-title") { SectionTitle(R.string.genre_albums) }
    content.albums.chunked(content.albumColumns).forEachIndexed { index, row ->
        item(key = "albums-$index") {
            Row(
                Modifier.padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { album -> AlbumCard(album, onClick = { onOpenAlbum(album.id) }, Modifier.weight(1f)) }
                // Keeps the last row's cards the same width as the others.
                repeat(content.albumColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
