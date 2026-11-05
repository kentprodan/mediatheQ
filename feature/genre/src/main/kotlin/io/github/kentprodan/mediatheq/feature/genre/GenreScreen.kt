package io.github.kentprodan.mediatheq.feature.genre

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.GenreHeaderBackground
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.LocalMqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.imageColorScheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColors
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.ArtistCard
import io.github.kentprodan.mediatheq.core.ui.TrackRow

@Composable
internal fun GenreRoute(
    genreId: MediaId,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onOpenTopTracks: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    onBack: () -> Unit,
    viewModel: GenreViewModel =
        hiltViewModel<GenreViewModel, GenreViewModel.Factory>(key = genreId.toString(), creationCallback = {
            it.create(genreId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenreScreen(
        state,
        viewModel::onAction,
        onOpenAlbum,
        onOpenArtist,
        onBack,
        onOpenTopTracks = onOpenTopTracks,
        onAddToPlaylist = onAddToPlaylist,
    )
}

/**
 * Stateless genre page (#130, #134): the header (background, name, counts, buttons), then the sections in the chosen
 * order: artists, album artists, albums, top tracks. With dynamic colors on the page takes the header image's scheme.
 */
@Composable
fun GenreScreen(
    state: GenreUiState,
    onAction: (GenreAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTopTracks: () -> Unit = {},
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
    /** Screenshots: these colors instead of the image's (previews load no images). */
    imageColors: CoverColors? = null,
) {
    val content = state as? GenreUiState.Content
    GenreColors(content, imageColors) { imageColor ->
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val drawsUnderBar = content != null && content.page.headerBackground != GenreHeaderBackground.None
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    // The header shows the name; the bar names the genre only once it has nearly collapsed.
                    title = {
                        Text(
                            content?.genre?.name.orEmpty(),
                            Modifier.graphicsLayer { alpha = titleAlpha(scrollBehavior.state.collapsedFraction) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.genre_back))
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
            // The app shell already pads for the navigation bar.
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            when {
                state == GenreUiState.Loading -> {
                    Unit
                }

                content == null -> {
                    NotFound(Modifier.padding(padding), onBack)
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(top = if (drawsUnderBar) 0.dp else padding.calculateTopPadding())
                                .plusBottomBars(),
                    ) {
                        item(key = "header") {
                            GenreHeader(
                                content,
                                topPadding = if (drawsUnderBar) padding.calculateTopPadding() else 0.dp,
                                imageColor = imageColor,
                                onAction = onAction,
                                onAddToPlaylist = onAddToPlaylist,
                            )
                        }
                        content.sections.forEach { section ->
                            when (section) {
                                GenreSection.Artists -> {
                                    artistRow("artists", R.string.genre_artists, content.trackArtists, onOpenArtist)
                                }

                                GenreSection.AlbumArtists -> {
                                    artistRow(
                                        "album-artists",
                                        R.string.genre_album_artists,
                                        content.albumArtists,
                                        onOpenArtist,
                                    )
                                }

                                GenreSection.Albums -> {
                                    albumGrid(content, onOpenAlbum)
                                }

                                GenreSection.TopTracks -> {
                                    topTracks(content, onAction, onOpenTopTracks)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The header image's colors (ADR 0029): the page's scheme with dynamic colors on, the gradient's color either way. */
@Composable
private fun GenreColors(
    content: GenreUiState.Content?,
    override: CoverColors?,
    page: @Composable (imageColor: Color?) -> Unit,
) {
    val cache = remember { CoverColorCache() }
    val seed = content?.headerArtwork
    val colors = override ?: rememberCoverColors(seed?.let { "${it.itemId}/${it.tag}" }, seed, cache)
    val theme = LocalMqTheme.current
    val dynamic = content?.page?.dynamicColors == true
    val scheme = remember(colors, theme, dynamic) { imageColorScheme(colors, theme).takeIf { dynamic } }
    MqCoverTheme(scheme) { page((scheme ?: colors?.scheme(theme.isDark, vibrant = false))?.primaryContainer) }
}

@Composable
private fun NotFound(
    modifier: Modifier,
    onBack: () -> Unit,
) {
    MqEmptyState(
        MqIcons.Genres,
        stringResource(R.string.genre_not_found_title),
        modifier,
        body = stringResource(R.string.genre_not_found_body),
        actionLabel = stringResource(R.string.genre_back),
        onAction = onBack,
    )
}

@Composable
private fun SectionTitle(
    @StringRes title: Int,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.s,
                top = MediatheqSpacing.l,
                bottom = MediatheqSpacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        action?.invoke()
    }
}

/** Artists in a row: credited artists without a picture get the person placeholder. */
private fun LazyListScope.artistRow(
    key: String,
    @StringRes title: Int,
    artists: List<Artist>,
    onOpenArtist: (MediaId) -> Unit,
) {
    item(key = "$key-title") { SectionTitle(title) }
    item(key = key) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            items(artists, key = { it.id.toString() }) { artist ->
                ArtistCard(artist, { onOpenArtist(artist.id) }, Modifier.width(MediatheqSizes.albumCardInRow))
            }
        }
    }
}

/** The albums as rows of the page's list with the Library's column count (a nested lazy grid cannot scroll in it). */
private fun LazyListScope.albumGrid(
    content: GenreUiState.Content,
    onOpenAlbum: (MediaId) -> Unit,
) {
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

/** The most played tracks, as many as the setting asks; "Show all" opens every track. */
private fun LazyListScope.topTracks(
    content: GenreUiState.Content,
    onAction: (GenreAction) -> Unit,
    onShowAll: () -> Unit,
) {
    item(key = "top-title") {
        SectionTitle(R.string.genre_top_tracks) {
            if (content.tracks.size > content.topTracks.size) {
                TextButton(onClick = onShowAll) { Text(stringResource(R.string.genre_show_all)) }
            }
        }
    }
    items(content.topTracks, key = { "top-${it.id}" }) { track -> GenreTrackRow(track, content, onAction, all = false) }
}

@Composable
private fun GenreTrackRow(
    track: Track,
    content: GenreUiState.Content,
    onAction: (GenreAction) -> Unit,
    all: Boolean,
) {
    TrackRow(
        track,
        onClick = { onAction(GenreAction.PlayTrack(track, all)) },
        showCover = true,
        isCurrent = track.id == content.currentTrackId,
    )
}

/** Hidden while the bar is open, fading in over the last part of the collapse. */
private fun titleAlpha(collapsedFraction: Float): Float =
    ((collapsedFraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)

private const val TITLE_FADE_START = 0.7f

@Composable
internal fun GenreTopTracksRoute(
    genreId: MediaId,
    onBack: () -> Unit,
    // Its own instance for the same genre: Navigation 3 scopes view models to their entry.
    viewModel: GenreViewModel =
        hiltViewModel<GenreViewModel, GenreViewModel.Factory>(key = "$genreId/top", creationCallback = {
            it.create(genreId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenreTopTracksScreen(state, viewModel::onAction, onBack)
}

/** "Show all" of the top tracks (#134): every track of the genre, most played first. */
@Composable
fun GenreTopTracksScreen(
    state: GenreUiState,
    onAction: (GenreAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? GenreUiState.Content
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    GenreColors(content, null) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.genre_top_tracks)) },
                    subtitle = { Text(content?.genre?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.genre_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            when {
                state == GenreUiState.Loading -> {
                    Unit
                }

                content == null -> {
                    NotFound(Modifier.padding(padding), onBack)
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding()).plusBottomBars(),
                    ) {
                        items(content.rankedTracks, key = { it.id.toString() }) { track ->
                            GenreTrackRow(track, content, onAction, all = true)
                        }
                    }
                }
            }
        }
    }
}
