package io.github.kentprodan.mediatheq.feature.artist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqArtistAvatar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayShuffleButtons
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount

@Composable
internal fun ArtistRoute(
    artistId: MediaId,
    onOpenAlbum: (MediaId) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel =
        hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = artistId.toString(), creationCallback = {
            it.create(artistId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistScreen(state, viewModel::onAction, onOpenAlbum, onBack)
}

/** Stateless artist page: avatar and facts, Play / Shuffle, top tracks, albums, then albums the artist appears on. */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onAction: (ArtistAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val content = state as? ArtistUiState.Content
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(content?.artist?.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.artist_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // The app shell already pads for the navigation bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            state == ArtistUiState.Loading -> {
                Unit
            }

            content == null -> {
                MqEmptyState(
                    MqIcons.Person,
                    stringResource(R.string.artist_not_found_title),
                    Modifier.padding(padding),
                    body = stringResource(R.string.artist_not_found_body),
                    actionLabel = stringResource(R.string.artist_back),
                    onAction = onBack,
                )
            }

            content.isEmpty -> {
                MqEmptyState(
                    MqIcons.Person,
                    stringResource(R.string.artist_empty_title),
                    Modifier.padding(padding),
                    body = stringResource(R.string.artist_empty_body),
                )
            }

            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = MediatheqSpacing.l),
                ) {
                    item(key = "header") { ArtistHeader(content) }
                    item(key = "buttons") {
                        MqPlayShuffleButtons(
                            enabled = content.tracks.isNotEmpty(),
                            onPlay = { onAction(ArtistAction.Play) },
                            onShuffle = { onAction(ArtistAction.Shuffle) },
                        )
                    }
                    if (content.topTracks.isNotEmpty()) {
                        item(key = "top-title") { SectionTitle(R.string.artist_top_tracks) }
                        items(content.topTracks, key = { "top-${it.id}" }) { track ->
                            TrackRow(
                                track,
                                onClick = { onAction(ArtistAction.PlayTopTrack(track.id)) },
                                showCover = true,
                                isCurrent = track.id == content.currentTrackId,
                            )
                        }
                    }
                    albumGrid("albums", R.string.artist_albums, content.albums, content, onOpenAlbum)
                    albumGrid("appears-on", R.string.artist_appears_on, content.appearsOn, content, onOpenAlbum)
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(content: ArtistUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
    ) {
        MqArtistAvatar(
            content.artist.artwork,
            Modifier.fillMaxWidth(AVATAR_WIDTH_FRACTION),
            content.artist.artwork?.blurHash,
        )
        Text(
            listOfNotNull(
                content.albums.size
                    .takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.artist_album_count, it, it) },
                formatTrackCount(content.tracks.size),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(
    @StringRes title: Int,
) {
    Text(
        stringResource(title),
        Modifier
            .padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.l)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * A titled grid of [albums] with the Library's column count, as rows of the page's list: a nested lazy grid
 * cannot scroll inside it, and the track rows above must keep their full width.
 */
private fun LazyListScope.albumGrid(
    key: String,
    @StringRes title: Int,
    albums: List<Album>,
    content: ArtistUiState.Content,
    onOpenAlbum: (MediaId) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = "$key-title") { SectionTitle(title) }
    albums.chunked(content.albumColumns).forEachIndexed { index, row ->
        item(key = "$key-$index") {
            Row(
                Modifier.padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                row.forEach { album -> AlbumCard(album, onClick = { onOpenAlbum(album.id) }, Modifier.weight(1f)) }
                // Keeps the last row's cards the same width as the others.
                repeat(content.albumColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val AVATAR_WIDTH_FRACTION = 0.5f
