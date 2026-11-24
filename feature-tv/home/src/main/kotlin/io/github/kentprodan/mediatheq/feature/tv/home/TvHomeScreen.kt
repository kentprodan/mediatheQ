package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.GenreItem
import io.github.kentprodan.mediatheq.core.data.GenreRow
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.data.genreRows
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.designsystem.tv.FocusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCardStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCarousel
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvBackdrop
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvRowHeader
import io.github.kentprodan.mediatheq.core.ui.tv.POSTER_RATIO
import io.github.kentprodan.mediatheq.core.ui.tv.title
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Where a cover leads. */
data class TvHomeNavigation(
    val onOpenAlbum: (MediaId) -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenPlaylist: (MediaId) -> Unit = {},
    /** Video (#431): a movie or series page, or a video played from where it was left. */
    val onOpenMovie: (MediaId) -> Unit = {},
    val onOpenSeries: (MediaId) -> Unit = {},
    val onPlayVideo: (MediaId, Duration) -> Unit = { _, _ -> },
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
            Rows(state, onAction, navigation, modifier)
        }
    }
}

/** A cover's menu: what it acts on, the name it shows and the artist its Go to artist opens (#346). */
private data class Menu(
    val target: TvHomeTarget,
    val title: String,
    val artistId: MediaId? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rows(
    state: TvHomeUiState.Content,
    onAction: (TvHomeAction) -> Unit,
    navigation: TvHomeNavigation,
    modifier: Modifier,
) {
    val rows = state.rows
    val genreRows = state.genreRows
    val cardStyles = state.cardStyles
    val focus = rememberFocusMemory()
    var menu by remember { mutableStateOf<Menu?>(null) }
    var videoMenu by remember { mutableStateOf<Video?>(null) }
    // Immersive rows (#521): the focused card is written here by the cards but read only by the immersive layer of the
    // row that has the focus (after a pause, so moving along a row never flickers): a D-pad move recomposes the cards
    // it touches only.
    val focused = remember { HeroFocus() }
    val hero = rememberHero(focused)
    val showCarousel = state.carousel != TvCarousel.Off && state.featured.isNotEmpty()
    val cards =
        remember(focus, onAction, navigation, cardStyles) {
            Cards(
                focus,
                cardStyles,
                onAction,
                navigation,
                onFocused = { focused.info = it },
                openVideoMenu = { videoMenu = it },
            ) { menu = it }
        }
    // Genre rows (#431) follow the last video row.
    val split = rows.indexOfLast { it.items is HomeItems.Videos } + 1
    val immersive = state.immersiveRows
    val sectionRow: @Composable (TvHomeRow) -> Unit = { row ->
        val title = stringResource(row.section.title)
        if (row.section in immersive) {
            ImmersiveRow(row.section.name, title, focused, hero) { with(cards) { row(row) } }
        } else {
            CardRow(title, Modifier.rowFocus(focused, row.section.name)) { with(cards) { row(row) } }
        }
    }
    val list = rememberLazyListState()
    val rowsToTop = remember(list) { rowsToTop(list) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rowsToTop) {
        LazyColumn(
            modifier.fillMaxSize(),
            state = list,
            contentPadding =
                PaddingValues(
                    top = if (showCarousel && state.carousel == TvCarousel.Immersive) 0.dp else 32.dp,
                    bottom = 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (showCarousel) {
                item(key = "featured", contentType = "featured") {
                    FeaturedCarousel(state, focus, onAction, navigation, Modifier.rowFocus(focused, FEATURED_KEY))
                }
            }
            items(rows.take(split), key = { it.section.name }, contentType = { "row" }) { sectionRow(it) }
            items(genreRows, key = { "genre/${it.genre}" }, contentType = { "row" }) { row ->
                CardRow(row.genre, Modifier.rowFocus(focused, "genre/${row.genre}")) { with(cards) { genre(row) } }
            }
            items(rows.drop(split), key = { it.section.name }, contentType = { "row" }) { sectionRow(it) }
        }
    }
    // The carousel or the first card on the first visit, the one focused last when coming back from a detail page.
    LaunchedEffect(Unit) {
        // Lazy rows compose their cards when laid out, after this effect starts: wait for that frame.
        withFrameNanos {}
        if (showCarousel) {
            focus.restore(FEATURED_KEY)
        } else {
            rows.firstNotNullOfOrNull(::firstKey)?.let(focus::restore)
        }
    }
    menu?.let { shown -> CoverMenu(shown, onAction, navigation.onOpenArtist) { menu = null } }
    videoMenu?.let { video -> ResumeMenu(video, onAction, navigation) { videoMenu = null } }
}

/** A titled row of cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MqTvRowHeader(title, Modifier.padding(horizontal = EDGE))
        // Focus coming back into a row lands on the card it left (the column position is kept).
        CompositionLocalProvider(LocalBringIntoViewSpec provides Pivot) {
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(horizontal = EDGE, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
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
        is HomeItems.Videos -> items.videos.firstOrNull()?.id
    }?.let { focusKey(row, it) }

/** The cards of one row, by what the section holds. */
private class Cards(
    val focus: FocusMemory,
    val cardStyles: Map<HomeSection, TvCardStyle>,
    val onAction: (TvHomeAction) -> Unit,
    val navigation: TvHomeNavigation,
    val onFocused: (HeroInfo?) -> Unit,
    val openVideoMenu: (Video) -> Unit,
    val openMenu: (Menu) -> Unit,
) {
    /** The row's variant from Settings (#518), else [default]. */
    fun style(
        row: TvHomeRow,
        default: MqTvCardStyle,
    ): MqTvCardStyle = cardStyles[row.section]?.let { MqTvCardStyle.valueOf(it.name) } ?: default

    fun LazyListScope.row(row: TvHomeRow) {
        val standard = style(row, MqTvCardStyle.Standard)
        when (val items = row.items) {
            is HomeItems.Albums -> {
                itemsIndexed(items.albums, key = { _, it -> it.id.toString() }) { index, album ->
                    Card(
                        focusKey(row, album.id),
                        album.title,
                        album.artwork,
                        names(album.albumArtists),
                        Menu(TvHomeTarget.Album(album.id), album.title, album.albumArtists.firstOrNull()?.id),
                        style = standard,
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
                        standard,
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
                        style = standard,
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
                        Menu(TvHomeTarget.Song(track), track.title, track.artists.firstOrNull()?.id),
                        style = standard,
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
                    Card(
                        focusKey(row, mix),
                        title,
                        null,
                        null,
                        null,
                        icon,
                        style(row, MqTvCardStyle.Compact),
                    ) { onAction(TvHomeAction.PlayMix(mix)) }
                }
            }

            is HomeItems.Videos -> {
                val plays = row.section == HomeSection.ContinueWatching || row.section == HomeSection.NextUp
                itemsIndexed(items.videos, key = { _, it -> it.id.toString() }) { _, video ->
                    VideoCard(row, video, items.wide, plays)
                }
            }
        }
    }

    /** A genre row (#431): posters; Select opens the movie or the series. */
    fun LazyListScope.genre(row: GenreRow) {
        itemsIndexed(row.items, key = { _, it -> it.id.toString() }) { _, item ->
            val movie = (item as? GenreItem.Movie)?.video
            val series = (item as? GenreItem.Show)?.series
            MqTvPosterCard(
                movie?.title ?: series?.title.orEmpty(),
                movie?.artwork ?: series?.artwork,
                onClick = { if (movie != null) navigation.onOpenMovie(movie.id) else navigation.onOpenSeries(item.id) },
                modifier =
                    Modifier
                        .width(POSTER_CARD)
                        .focusMemory(focus, "genre/${row.genre}/${item.id}")
                        .onFocusChanged {
                            if (it.isFocused) onFocused(movie?.let(::heroInfo) ?: series?.let(::heroInfo))
                        },
                subtitle = (movie?.year ?: series?.year)?.toString(),
                watched = (movie?.userData ?: series?.userData)?.played == true,
                placeholder = if (movie != null) MqTvIcons.Movie else MqTvIcons.Series,
                style = MqTvCardStyle.Standard,
            )
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
        style: MqTvCardStyle = MqTvCardStyle.Standard,
        onClick: () -> Unit,
    ) {
        val open = menu?.let { { openMenu(it) } }
        MqTvAlbumCard(
            title,
            artwork,
            onClick,
            Modifier
                .focusMemory(focus, key)
                .onFocusChanged { if (it.isFocused) onFocused(HeroInfo(title, subtitle, null, artwork)) }
                .onPreviewKeyEvent { event ->
                    // The remote's menu key does what a long-press does.
                    val isMenu = event.key == Key.Menu && open != null
                    if (isMenu && event.type == KeyEventType.KeyUp) open?.invoke()
                    isMenu
                },
            subtitle = subtitle,
            placeholder = placeholder,
            onLongClick = open,
            style = style,
        )
    }

    private fun names(artists: List<Artist>): String? = artists.joinToString { it.name }.ifEmpty { null }

    /**
     * A movie or episode (#431): wide stills with progress and the time left for Continue watching and Next up (Select
     * plays), posters for new movies, one card per series for new episodes (Select opens the page).
     */
    @Composable
    private fun VideoCard(
        row: TvHomeRow,
        video: Video,
        stills: Boolean,
        plays: Boolean,
    ) {
        // The row's variant (#518): 16:9 stills as Wide classic, posters as Standard; a wide choice shows the backdrop.
        val cardStyle = style(row, if (stills) MqTvCardStyle.WideClassic else MqTvCardStyle.Standard)
        val wide = cardStyle.wide
        val episode = video.kind == VideoKind.Episode
        val newCount = video.seriesId?.let { row.newEpisodes[it] }
        val title = if (episode) video.seriesName ?: video.title else video.title
        val left = (video.duration - video.resumePosition()).takeIf { video.resumePosition() > Duration.ZERO }
        val subtitle =
            when {
                newCount != null && newCount > 1 -> {
                    pluralStringResource(
                        R.plurals.tv_home_new_episodes,
                        newCount,
                        newCount,
                    )
                }

                left != null -> {
                    // An episode says which one, like Next up: "S2 · E5 · 34 min left".
                    val time = stringResource(R.string.tv_home_time_left, left.inWholeMinutes.coerceAtLeast(1))
                    if (episode) "${episodeLabel(video)} · $time" else time
                }

                episode -> {
                    episodeLabel(video)
                }

                else -> {
                    video.year?.toString()
                }
            }
        // Continue watching cards have a menu (#431): long-press or the remote's menu key.
        val open = { openVideoMenu(video) }.takeIf { row.section == HomeSection.ContinueWatching }
        MqTvPosterCard(
            title,
            if (wide) video.backdrop ?: video.artwork else video.artwork,
            onClick = {
                when {
                    plays -> navigation.onPlayVideo(video.id, video.resumePosition())
                    episode -> video.seriesId?.let(navigation.onOpenSeries)
                    else -> navigation.onOpenMovie(video.id)
                }
            },
            modifier =
                Modifier
                    .focusMemory(focus, focusKey(row, video.id))
                    .onFocusChanged { if (it.isFocused) onFocused(heroInfo(video)) }
                    .onPreviewKeyEvent { event ->
                        val isMenu = event.key == Key.Menu && open != null
                        if (isMenu && event.type == KeyEventType.KeyUp) open?.invoke()
                        isMenu
                    },
            subtitle = subtitle,
            progress = video.userData.playedPercentage?.let { (it / 100).toFloat() },
            placeholder = if (episode) MqTvIcons.Series else MqTvIcons.Movie,
            aspectRatio = if (wide) 16f / 9f else POSTER_RATIO,
            onLongClick = open,
            style = cardStyle,
            description = video.overview,
        )
    }
}

@Composable
private fun CoverMenu(
    menu: Menu,
    onAction: (TvHomeAction) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onDismiss: () -> Unit,
) = MqTvMenu(
    menu.title,
    listOfNotNull(
        MqTvMenuEntry(
            stringResource(R.string.tv_home_play),
            MqTvIcons.Play,
        ) { onAction(TvHomeAction.Play(menu.target)) },
        MqTvMenuEntry(stringResource(R.string.tv_home_shuffle), MqTvIcons.Shuffle) {
            onAction(TvHomeAction.Play(menu.target, shuffle = true))
        },
        MqTvMenuEntry(stringResource(R.string.tv_home_add_to_queue), MqTvIcons.Add) {
            onAction(TvHomeAction.AddToQueue(menu.target))
        },
        menu.artistId?.let { artist ->
            MqTvMenuEntry(stringResource(R.string.tv_home_go_to_artist), MqTvIcons.Person) { onOpenArtist(artist) }
        },
    ),
    onDismiss,
)

/** A Continue watching card's menu (#431): its page, or out of the row. */
@Composable
private fun ResumeMenu(
    video: Video,
    onAction: (TvHomeAction) -> Unit,
    navigation: TvHomeNavigation,
    onDismiss: () -> Unit,
) {
    val episode = video.kind == VideoKind.Episode
    MqTvMenu(
        if (episode) video.seriesName ?: video.title else video.title,
        listOfNotNull(
            if (episode) {
                video.seriesId?.let { series ->
                    MqTvMenuEntry(stringResource(R.string.tv_home_go_to_series), MqTvIcons.Series) {
                        navigation.onOpenSeries(series)
                    }
                }
            } else {
                MqTvMenuEntry(stringResource(R.string.tv_home_go_to_movie), MqTvIcons.Movie) {
                    navigation.onOpenMovie(video.id)
                }
            },
            MqTvMenuEntry(stringResource(R.string.tv_home_remove_from_resume), null) {
                onAction(TvHomeAction.RemoveFromResume(video.id))
            },
        ),
        onDismiss,
    )
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

/** What the hero shows for a card (#504): any card, so the top of Home is never empty. */
private data class HeroInfo(
    val title: String,
    val facts: String?,
    val overview: String?,
    /** A video's backdrop, else the card's own picture. */
    val backdrop: Artwork?,
)

private fun heroInfo(video: Video): HeroInfo {
    val episode = video.kind == VideoKind.Episode
    val facts =
        listOfNotNull(
            if (episode) episodeLabel(video) + " · " + video.title else video.year?.toString(),
            video.officialRating,
            video.genres
                .take(2)
                .joinToString(", ")
                .ifEmpty { null },
        ).joinToString(" · ").ifEmpty { null }
    return HeroInfo(
        if (episode) video.seriesName ?: video.title else video.title,
        facts,
        video.overview,
        video.backdrop ?: video.artwork,
    )
}

private fun heroInfo(series: Series): HeroInfo =
    HeroInfo(
        series.title,
        listOfNotNull(
            series.year?.toString(),
            series.genres
                .take(2)
                .joinToString(", ")
                .ifEmpty { null },
        ).joinToString(" · ")
            .ifEmpty { null },
        series.overview,
        series.backdrop ?: series.artwork,
    )

/** The card and the row last focused (#521); only the immersive layers read them. */
@Stable
private class HeroFocus {
    var info by mutableStateOf<HeroInfo?>(null)
    var row by mutableStateOf<String?>(null)
}

/**
 * The focused row and card once they rested for [HERO_DELAY], debounced together so a row never shows the card of the
 * row the focus left; the state is read only by the immersive layers.
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberHero(focus: HeroFocus): State<Pair<String?, HeroInfo?>> =
    produceState<Pair<String?, HeroInfo?>>(null to null, focus) {
        snapshotFlow { focus.row to focus.info }.debounce(HERO_DELAY).collect { value = it }
    }

/**
 * The focused row lands near the top, at the same place every time, like Google TV. While Home is still at its top and
 * the row is wholly on screen (the first row under the carousel, #574), nothing moves: the carousel stays in view and
 * its slides advance.
 */
private fun rowsToTop(list: LazyListState) =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float =
            if (!list.canScrollBackward && offset >= 0 && offset + size <= containerSize) 0f else offset - ROW_HEADER_PX
    }

// The row title and its padding above the cards, so the title stays in view.
private const val ROW_HEADER_PX = 140f

/** A card asking to be shown brings its whole immersive row instead (#521), so the details above the cards stay on screen. */
private fun Modifier.showsWholeRow(): Modifier = this then WholeRowElement

private data object WholeRowElement : ModifierNodeElement<WholeRowNode>() {
    override fun create() = WholeRowNode()

    override fun update(node: WholeRowNode) = Unit
}

private class WholeRowNode :
    Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        // rowsToTop lands a request's top ROW_HEADER_PX down: start it that far into the row so the row's top lands at 0.
        bringIntoView {
            requireLayoutCoordinates()
                .size
                .toSize()
                .toRect()
                .translate(0f, ROW_HEADER_PX)
        }
    }
}

/** Marks the row that has the focus (#521), so only its immersive layer shows; read only by that layer. */
private fun Modifier.rowFocus(
    focused: HeroFocus,
    key: String,
): Modifier = onFocusChanged { if (it.hasFocus) focused.row = key }

/**
 * An immersive list (#521, Google TV guidelines): while the row has the focus, the focused card's 16:9 backdrop
 * aligned top right under a cinematic scrim and its details, revealed once the focus rests; the row keeps one fixed
 * height, so nothing around it moves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImmersiveRow(
    key: String,
    title: String,
    focused: HeroFocus,
    hero: State<Pair<String?, HeroInfo?>>,
    content: LazyListScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(IMMERSIVE_ROW_HEIGHT)
            .rowFocus(focused, key)
            .showsWholeRow(),
    ) {
        // Hidden at once when the focus leaves the row, shown only after it rested on a card here.
        val info = hero.value.takeIf { (row) -> row == key && focused.row == key }?.second
        AnimatedVisibility(
            visible = info != null,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(REVEAL_MILLIS)),
            exit = fadeOut(tween(REVEAL_MILLIS)),
        ) {
            // The last info stays while it fades out.
            val shown = remember { mutableStateOf(info) }
            if (info != null) shown.value = info
            Box(Modifier.fillMaxSize()) {
                MqTvBackdrop(
                    shown.value?.backdrop,
                    Modifier.align(Alignment.TopEnd).fillMaxWidth(BACKDROP_WIDTH).aspectRatio(16f / 9f),
                ) {}
                shown.value?.let { Details(it, Modifier.padding(start = EDGE, top = 16.dp)) }
            }
        }
        CardRow(title, Modifier.align(Alignment.BottomStart), content)
    }
}

/** The immersive row's details: the focused card's title, facts and two lines of overview. */
@Composable
private fun Details(
    info: HeroInfo,
    modifier: Modifier,
) {
    Column(modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(info.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        info.facts?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info.overview?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The featured carousel (#521): Card is an inset rounded card, Immersive spans the top of Home. Each slide shows its
 * picture under a scrim with the section as overline, the title, a short description and Play.
 */
@Composable
private fun FeaturedCarousel(
    state: TvHomeUiState.Content,
    focus: FocusMemory,
    onAction: (TvHomeAction) -> Unit,
    navigation: TvHomeNavigation,
    modifier: Modifier,
) {
    val immersive = state.carousel == TvCarousel.Immersive
    MqTvCarousel(
        state.featured.size,
        if (immersive) {
            modifier.fillMaxWidth().height(IMMERSIVE_CAROUSEL_HEIGHT)
        } else {
            modifier
                .padding(horizontal = EDGE)
                .fillMaxWidth()
                .height(CARD_CAROUSEL_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
        },
    ) { index ->
        val item = state.featured[index]
        val slide = slideOf(item)
        MqTvBackdrop(slide.picture, Modifier.fillMaxSize()) {}
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (immersive) EDGE else 32.dp, bottom = 32.dp, end = 32.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource((state.slideSections[item.id] ?: state.featuredSection).title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                slide.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            slide.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            MqTvButton(
                stringResource(R.string.tv_home_play),
                {
                    when (item) {
                        is Featured.Movie -> navigation.onPlayVideo(item.video.id, item.video.resumePosition())
                        is Featured.AlbumItem -> onAction(TvHomeAction.Play(TvHomeTarget.Album(item.id)))
                        is Featured.PlaylistItem -> onAction(TvHomeAction.Play(TvHomeTarget.Playlist(item.id)))
                    }
                },
                Modifier.padding(top = 8.dp).focusMemory(focus, FEATURED_KEY),
                icon = MqTvIcons.Play,
            )
        }
    }
}

/** What a slide shows. */
private data class Slide(
    val title: String,
    val description: String?,
    val picture: Artwork?,
)

private fun slideOf(item: Featured): Slide =
    when (item) {
        is Featured.Movie -> {
            val info = heroInfo(item.video)
            Slide(info.title, item.video.overview ?: info.facts, info.backdrop)
        }

        is Featured.AlbumItem -> {
            Slide(
                item.album.title,
                item.album.albumArtists
                    .joinToString { it.name }
                    .ifEmpty { null },
                item.album.artwork,
            )
        }

        is Featured.PlaylistItem -> {
            Slide(item.playlist.name, null, item.playlist.artwork)
        }
    }

/** The carousel's Play in the focus memory: the first visit starts there. */
private const val FEATURED_KEY = "featured"

private val CARD_CAROUSEL_HEIGHT = 300.dp
private val IMMERSIVE_CAROUSEL_HEIGHT = 400.dp
private val IMMERSIVE_ROW_HEIGHT = 420.dp
private const val BACKDROP_WIDTH = 0.66f
private const val REVEAL_MILLIS = 300

/** "S2 · E5", or nothing known. */
private fun episodeLabel(video: Video): String =
    listOfNotNull(video.seasonNumber?.let { "S$it" }, video.episodeNumber?.let { "E$it" }).joinToString(" · ")

// Guideline card width (#507): 5 posters per screen, 20 dp apart; the wide variants size themselves (#518).
private val POSTER_CARD = 124.dp
private val HERO_DELAY = 500.milliseconds
