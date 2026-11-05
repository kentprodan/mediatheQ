package io.github.kentprodan.mediatheq.feature.artist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.ArtistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.LocalMqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.imageColorScheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColors
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.SelectableList
import io.github.kentprodan.mediatheq.core.ui.rememberSelectLongPress
import io.github.kentprodan.mediatheq.core.ui.trackSwipes

@Composable
internal fun ArtistRoute(
    artistId: MediaId,
    onOpenAlbum: (MediaId) -> Unit,
    onOpenTopTracks: () -> Unit,
    onBack: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    viewModel: ArtistViewModel =
        hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = artistId.toString(), creationCallback = {
            it.create(artistId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistScreen(
        state,
        viewModel::onAction,
        onOpenAlbum,
        onBack,
        onAddToPlaylist = onAddToPlaylist,
        onOpenTopTracks = onOpenTopTracks,
    )
}

/**
 * Stateless artist page (#133): the header (background, name, facts, buttons), then the sections in the chosen order:
 * albums, top tracks, appears on, about. With dynamic colors on the whole page takes the artist image's scheme.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onAction: (ArtistAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
    onOpenTopTracks: () -> Unit = {},
    /** Screenshots: these colors instead of the artist image's (previews load no images). */
    imageColors: CoverColors? = null,
) {
    val content = state as? ArtistUiState.Content
    ArtistColors(content, imageColors) { imageColor ->
        ArtistPage(
            state,
            content,
            imageColor,
            onAction,
            onOpenAlbum,
            onBack,
            modifier,
            onAddToPlaylist,
            onOpenTopTracks,
        )
    }
}

/**
 * The artist image's colors (ADR 0029): the page's scheme when dynamic colors are on, and the header gradient's color
 * either way. The image, else the backdrop, seeds them.
 */
@Composable
internal fun ArtistColors(
    content: ArtistUiState.Content?,
    override: CoverColors? = null,
    page: @Composable (imageColor: Color?) -> Unit,
) {
    val cache = remember { CoverColorCache() }
    val seed = content?.artist?.let { it.artwork ?: it.backdrop }
    val colors = override ?: rememberCoverColors(seed?.let { "${it.itemId}/${it.tag}" }, seed, cache)
    val theme = LocalMqTheme.current
    val scheme =
        remember(colors, theme, content?.page?.dynamicColors) {
            imageColorScheme(colors, theme).takeIf { content?.page?.dynamicColors == true }
        }
    MqCoverTheme(scheme) {
        page((scheme ?: colors?.scheme(theme.isDark, vibrant = false))?.primaryContainer)
    }
}

@Composable
private fun ArtistPage(
    state: ArtistUiState,
    content: ArtistUiState.Content?,
    imageColor: Color?,
    onAction: (ArtistAction) -> Unit,
    onOpenAlbum: (MediaId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    onOpenTopTracks: () -> Unit,
) {
    val longPress = rememberSelectLongPress<MediaId> { onAction(ArtistAction.ToggleSelection(it)) }
    val swipe =
        trackSwipes(
            onAddToPlaylist,
            onRate = { track, stars -> onAction(ArtistAction.RateTrack(track, stars)) },
            onCommand = { track, right -> onAction(ArtistAction.SwipeTrack(track, right)) },
        )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val drawsUnderBar =
        content != null && !content.isEmpty && content.page.headerBackground != ArtistHeaderBackground.None
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                // The header shows the name; the bar names the artist only once it has nearly collapsed.
                title = {
                    Text(
                        content?.artist?.name.orEmpty(),
                        Modifier.graphicsLayer { alpha = titleAlpha(scrollBehavior.state.collapsedFraction) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.artist_back))
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
                LaunchedEffect(content.addToPlaylist) {
                    content.addToPlaylist?.let {
                        onAddToPlaylist(it)
                        onAction(ArtistAction.AddToPlaylistShown)
                    }
                }
                val columns = albumColumns(content)
                SelectableList(
                    content.selection,
                    onCommand = { onAction(ArtistAction.OnSelection(it)) },
                    onSelectAll = { onAction(ArtistAction.SelectAll) },
                    onClear = { onAction(ArtistAction.ClearSelection) },
                ) { bottomSpace ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                // A background draws under the top bar; the header pads itself.
                                top = if (drawsUnderBar) 0.dp else padding.calculateTopPadding(),
                                bottom = bottomSpace,
                            ).plusBottomBars(),
                    ) {
                        item(key = "header") {
                            ArtistHeader(
                                content,
                                topPadding = if (drawsUnderBar) padding.calculateTopPadding() else 0.dp,
                                imageColor = imageColor,
                                onAction = onAction,
                                onAddToPlaylist = onAddToPlaylist,
                            )
                        }
                        val openAlbum = { id: MediaId ->
                            if (content.selection.isActive) {
                                onAction(
                                    ArtistAction.ToggleSelection(id),
                                )
                            } else {
                                onOpenAlbum(id)
                            }
                        }
                        content.sections.forEach { section ->
                            when (section) {
                                ArtistSection.Albums -> {
                                    albumGrid(
                                        "albums",
                                        R.string.artist_albums,
                                        content.albums,
                                        columns,
                                        content,
                                        openAlbum,
                                        longPress,
                                    )
                                }

                                ArtistSection.AppearsOn -> {
                                    albumGrid(
                                        "appears-on",
                                        R.string.artist_appears_on,
                                        content.appearsOn,
                                        columns,
                                        content,
                                        openAlbum,
                                        longPress,
                                    )
                                }

                                ArtistSection.TopTracks -> {
                                    topTracks(content, onAction, onOpenTopTracks, longPress, swipe)
                                }

                                ArtistSection.About -> {
                                    item(key = "about") { AboutSection(content, onAction) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hidden while the bar is open, fading in over the last part of the collapse. */
internal fun titleAlpha(collapsedFraction: Float): Float =
    ((collapsedFraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)

private const val TITLE_FADE_START = 0.7f

@Composable
internal fun ArtistTopTracksRoute(
    artistId: MediaId,
    onBack: () -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
    // Its own instance for the same artist: Navigation 3 scopes view models to their entry.
    viewModel: ArtistViewModel =
        hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "$artistId/top", creationCallback = {
            it.create(artistId)
        }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistTopTracksScreen(state, viewModel::onAction, onBack, onAddToPlaylist = onAddToPlaylist)
}

/** "Show all" of the top tracks (#133): every played track of the artist, most played first. */
@Composable
fun ArtistTopTracksScreen(
    state: ArtistUiState,
    onAction: (ArtistAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (List<MediaId>) -> Unit = {},
) {
    val content = state as? ArtistUiState.Content
    val longPress = rememberSelectLongPress<MediaId> { onAction(ArtistAction.ToggleSelection(it)) }
    val swipe =
        trackSwipes(
            onAddToPlaylist,
            onRate = { track, stars -> onAction(ArtistAction.RateTrack(track, stars)) },
            onCommand = { track, right -> onAction(ArtistAction.SwipeTrack(track, right)) },
        )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ArtistColors(content) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.artist_top_tracks)) },
                    subtitle = {
                        Text(
                            content?.artist?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.artist_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            if (content != null) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()).plusBottomBars(),
                ) {
                    items(content.playedTracks, key = { it.id.toString() }) { track ->
                        ArtistTrackRow(track, content, onAction, longPress, swipe, all = true)
                    }
                }
            }
        }
    }
}
