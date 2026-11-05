package io.github.kentprodan.mediatheq.feature.nowplaying

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import io.github.kentprodan.mediatheq.core.data.CoverTap
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.ProgressFallback
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCoverPlayButton
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqIconToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayerCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqProgressFallback
import io.github.kentprodan.mediatheq.core.designsystem.component.MqSeekBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTransportControls
import io.github.kentprodan.mediatheq.core.designsystem.component.MqWaveformStyle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QualityLabel
import io.github.kentprodan.mediatheq.core.ui.formatDuration
import io.github.kentprodan.mediatheq.core.ui.formatFrequency
import io.github.kentprodan.mediatheq.core.ui.text
import kotlin.time.Duration

/** Where Now Playing sends the user: each opens a page or dialog behind the player, which closes first (#125). */
data class NowPlayingNavigation(
    val onOpenQueue: () -> Unit = {},
    val onOpenArtist: (MediaId) -> Unit = {},
    val onOpenAlbum: (MediaId) -> Unit = {},
    /** Settings -> Playback -> Audio (bit-perfect USB, equalizer). */
    val onOpenEqualizer: () -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
)

/**
 * The full player (#125), on a gradient from the (cover) theme's primary container to its surface. Portrait: cover (or
 * lyrics in its place), title block, waveform with times and quality, rating, controls, bottom bar. Landscape (wider
 * than tall): the cover square on the left, the rest scrolling on the right. Swiping down or the close button returns
 * to the mini player. [position] is read only by the seek bar and the lyrics, so position ticks recompose nothing else.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    lyrics: LyricsUiState = LyricsUiState.None,
    navigation: NowPlayingNavigation = NowPlayingNavigation(),
    cover: Any? = state.artwork,
    coverModifier: Modifier = Modifier,
    /** The app theme's background and its text color, for the Theme background style (#126). */
    themeBackground: Pair<Color, Color> = MaterialTheme.colorScheme.let { it.surface to it.onSurface },
) {
    val colors = MaterialTheme.colorScheme
    val paneTitle = stringResource(R.string.nowplaying_pane_title)
    // No Surface under the background, so set the text and icon color the matching surface would give.
    val (background, content) =
        when (state.settings.background) {
            PlayerBackground.Solid -> {
                // The cover scheme's tinted surface: every on-surface role (artist, times, label) keeps its contrast.
                SolidColor(colors.surfaceContainerHigh) to colors.onSurface
            }

            PlayerBackground.Gradient -> {
                Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)) to
                    colors.onSurface
            }

            PlayerBackground.Theme -> {
                SolidColor(themeBackground.first) to themeBackground.second
            }
        }
    CompositionLocalProvider(LocalContentColor provides content) {
        BoxWithConstraints(
            modifier
                .semantics { this.paneTitle = paneTitle }
                .fillMaxSize()
                .background(background)
                .swipeDownToCollapse(onCollapse)
                .safeDrawingPadding()
                .padding(horizontal = MediatheqSpacing.l),
        ) {
            val coverSquare: @Composable (Modifier) -> Unit = { square ->
                CoverOrLyrics(state, lyrics, position, onAction, cover, coverModifier, square)
            }
            // Read here: inside Row / Column the window size is out of scope.
            val showRatingInLandscape = maxHeight >= MediatheqSizes.playerRatingMinHeight
            if (maxWidth > maxHeight) {
                Row(
                    Modifier.fillMaxSize().padding(vertical = MediatheqSpacing.l),
                    horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coverSquare(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m, Alignment.CenterVertically),
                    ) {
                        // Sideways a phone is short: the bottom bar's buttons share the close button's row.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CollapseButton(onCollapse, Modifier.weight(1f))
                            BottomBarButtons(state, onAction, navigation)
                        }
                        // A phone held sideways has no room for the stars; a tablet has.
                        PlayerDetails(
                            state,
                            position,
                            onAction,
                            navigation,
                            showRating = showRatingInLandscape,
                            bottomBar = false,
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CollapseButton(onCollapse)
                    // Takes what is left, so the controls always fit (e.g. at fontScale 2.0).
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        coverSquare(Modifier)
                    }
                    PlayerDetails(state, position, onAction, navigation, showRating = true)
                }
            }
        }
    }
}

/** Everything under (or beside) the cover. */
@Composable
private fun ColumnScope.PlayerDetails(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
    navigation: NowPlayingNavigation,
    showRating: Boolean,
    bottomBar: Boolean = true,
) {
    TitleBlock(state, onAction, navigation)
    SeekSection(state, position, onAction)
    if (showRating && state.showsRating) {
        MqStarRating(
            halfStars = state.halfStars,
            onRate = { onAction(NowPlayingAction.Rate(it)) },
            allowHalf = state.ratingMode == RatingMode.HalfStars,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    Controls(state, onAction)
    if (bottomBar) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = MediatheqSpacing.s),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) { BottomBarButtons(state, onAction, navigation) }
    }
}

@Composable
private fun CollapseButton(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Box(modifier) {
        IconButton(onClick = onCollapse) {
            Icon(MqIcons.KeyboardArrowDown, contentDescription = stringResource(R.string.nowplaying_collapse))
        }
    }
}

/**
 * The cover, or the lyrics in its place (same square); the small round button at its bottom start switches. A
 * horizontal swipe changes track. Only the cover carries [coverModifier] (the shared flight to the mini player).
 */
@Composable
private fun CoverOrLyrics(
    state: NowPlayingUiState.Playing,
    lyrics: LyricsUiState,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
    cover: Any?,
    coverModifier: Modifier,
    modifier: Modifier,
) {
    val settings = state.settings
    val tapLabel =
        when (settings.coverTap) {
            CoverTap.PlayPause -> stringResource(R.string.nowplaying_cover_tap_play_pause)
            CoverTap.ShowLyrics -> stringResource(R.string.nowplaying_cover_tap_lyrics)
            CoverTap.None -> null
        }
    val coverName = stringResource(R.string.nowplaying_cover)
    val swipeActions = trackSwipeActions(state.hasPrevious, state.hasNext, onAction)
    Box(
        modifier.swipeToChangeTrack(
            state.hasPrevious,
            state.hasNext,
            onAction,
            enabled = settings.coverSwipe,
            accessibilityActions = false,
        ),
    ) {
        // Always there (under the lyrics too), so the cover still flies to and from the mini player. Its tap acts only
        // while it shows.
        MqPlayerCover(
            model = cover,
            modifier =
                coverModifier
                    .then(
                        if (tapLabel != null && !state.lyricsShown) {
                            Modifier
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable(onClickLabel = tapLabel, role = Role.Button) {
                                    onAction(NowPlayingAction.CoverTapped)
                                }
                        } else {
                            Modifier
                        },
                    ).semantics {
                        // One TalkBack stop for the cover: its name, and next / previous when the swipe is on.
                        contentDescription = coverName
                        if (settings.coverSwipe) customActions = swipeActions
                    },
            blurHash = state.artwork?.blurHash,
        )
        if (settings.playButtonWhenPaused && !state.isPlaying && !state.lyricsShown) {
            MqCoverPlayButton(
                onClick = { onAction(NowPlayingAction.TogglePlayPause) },
                contentDescription = stringResource(R.string.nowplaying_play_paused),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (state.lyricsShown) {
            Surface(
                Modifier.matchParentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                LyricsContent(lyrics, position, state.isSeekable, onAction)
            }
        }
        MqIconToggle(
            checked = state.lyricsShown,
            onClick = { onAction(NowPlayingAction.ToggleLyrics) },
            icon = MqIcons.Lyrics,
            contentDescription = stringResource(R.string.nowplaying_lyrics),
            modifier = Modifier.align(Alignment.BottomStart).padding(MediatheqSpacing.s),
        )
    }
}

/** Title (marquee) with favorite and the track menu, then the artists and the album, each opening its page. */
@Composable
private fun TitleBlock(
    state: NowPlayingUiState.Playing,
    onAction: (NowPlayingAction) -> Unit,
    navigation: NowPlayingNavigation,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                // Scrolls when too long, unless the system removed animations.
                modifier =
                    Modifier.semantics { heading() }.then(
                        if (animationsOn()) Modifier.basicMarquee() else Modifier,
                    ),
                overflow = TextOverflow.Ellipsis,
            )
            ArtistsLine(state, navigation)
            state.albumTitle?.let { album ->
                val albumId = state.albumId
                val openAlbum = stringResource(R.string.nowplaying_open_album)
                Text(
                    album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (albumId != null) {
                            Modifier.minimumInteractiveComponentSize().clickable(
                                onClickLabel = openAlbum,
                                role = Role.Button,
                            ) {
                                navigation.onOpenAlbum(albumId)
                            }
                        } else {
                            Modifier
                        },
                )
            }
        }
        MqFavoriteToggle(state.isFavorite, onToggle = { onAction(NowPlayingAction.SetFavorite(it)) })
        OverflowMenu(stringResource(R.string.nowplaying_more)) { close ->
            AddToPlaylistMenuItem(navigation.onAddToPlaylist, close)
            DownloadMenuItem({ onAction(NowPlayingAction.Download) }, close)
            state.albumId?.let { id ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nowplaying_go_to_album)) },
                    leadingIcon = { Icon(MqIcons.Library, contentDescription = null) },
                    onClick = {
                        close()
                        navigation.onOpenAlbum(id)
                    },
                )
            }
            state.artists.forEach { artist ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nowplaying_go_to_artist, artist.name)) },
                    leadingIcon = { Icon(MqIcons.Person, contentDescription = null) },
                    onClick = {
                        close()
                        navigation.onOpenArtist(artist.id)
                    },
                )
            }
        }
    }
}

/** The artists: one opens its page directly, several open a menu to pick from. */
@Composable
private fun ArtistsLine(
    state: NowPlayingUiState.Playing,
    navigation: NowPlayingNavigation,
) {
    var picking by remember { mutableStateOf(false) }
    val openArtist = stringResource(R.string.nowplaying_open_artist)
    Box {
        Text(
            state.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                if (state.artists.isNotEmpty()) {
                    Modifier.minimumInteractiveComponentSize().clickable(
                        onClickLabel = openArtist,
                        role = Role.Button,
                    ) {
                        val only = state.artists.singleOrNull()
                        if (only != null) navigation.onOpenArtist(only.id) else picking = true
                    }
                } else {
                    Modifier
                },
        )
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
            state.artists.forEach { artist ->
                DropdownMenuItem(
                    text = { Text(artist.name) },
                    leadingIcon = { Icon(MqIcons.Person, contentDescription = null) },
                    onClick = {
                        picking = false
                        navigation.onOpenArtist(artist.id)
                    },
                )
            }
        }
    }
}

/** The waveform; under it elapsed, the quality label (a tap switches kind and detail) and the time left. */
@Composable
private fun SeekSection(
    state: NowPlayingUiState.Playing,
    position: () -> Duration,
    onAction: (NowPlayingAction) -> Unit,
) {
    val elapsed = position().coerceIn(Duration.ZERO, state.duration)
    val progress = if (state.duration.isPositive()) (elapsed / state.duration).toFloat() else 0f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MqSeekBar(
            progress = progress,
            elapsedLabel = formatDuration(elapsed),
            totalLabel = formatDuration(state.duration),
            remainingLabel = "-" + formatDuration(state.duration - elapsed),
            onSeek = { onAction(NowPlayingAction.SeekTo(it)) },
            enabled = state.isSeekable,
            // A plain bar as the chosen style ignores the waveform; else the waveform, with the fallback until it is in.
            levels = state.waveform.takeIf { state.settings.progress.usesWaveform },
            style = state.settings.progress.waveformStyle,
            fallback =
                when {
                    state.settings.progress == PlayerProgress.Wavy -> MqProgressFallback.Wavy
                    state.settings.progress == PlayerProgress.Basic -> MqProgressFallback.Basic
                    state.settings.fallback == ProgressFallback.Wavy -> MqProgressFallback.Wavy
                    else -> MqProgressFallback.Basic
                },
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.quality?.let { QualityLabelButton(it, state.qualityDetail, onAction) }
        }
        state.bitPerfectSampleRateHz?.let { BitPerfectBadge(it) }
        if (state.atmosConverted) AtmosConvertedNote()
    }
}

/** What actually plays, as a kind ("Hi-Res Lossless") or its detail ("FLAC · 24/96 · 1250 kb/s"); a tap switches. */
@Composable
internal fun QualityLabelButton(
    label: QualityLabel,
    detail: Boolean,
    onAction: (NowPlayingAction) -> Unit,
) {
    val switchTo =
        stringResource(if (detail) R.string.nowplaying_quality_show_kind else R.string.nowplaying_quality_show_detail)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = switchTo, role = Role.Button) { onAction(NowPlayingAction.ToggleQualityDetail) }
            .padding(horizontal = MediatheqSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (detail) label.detail else stringResource(label.kind.text),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Repeat, previous, play/pause, next, shuffle. */
@Composable
private fun Controls(
    state: NowPlayingUiState.Playing,
    onAction: (NowPlayingAction) -> Unit,
) {
    // The mode shown is the one cycled from, so quick taps step through every mode.
    val nextRepeat = state.repeatMode.next()
    MqTransportControls(
        isPlaying = state.isPlaying,
        hasPrevious = state.hasPrevious,
        hasNext = state.hasNext,
        onPrevious = { onAction(NowPlayingAction.Previous) },
        onPlayPause = { onAction(NowPlayingAction.TogglePlayPause) },
        onNext = { onAction(NowPlayingAction.Next) },
        modifier = Modifier.fillMaxWidth(),
        leading = {
            MqIconToggle(
                checked = state.repeatMode != RepeatMode.Off,
                onClick = { onAction(NowPlayingAction.SetRepeatMode(nextRepeat)) },
                // Repeat one has its own icon; all and off differ in shape too, not by color alone.
                icon = if (state.repeatMode == RepeatMode.One) MqIcons.RepeatOne else MqIcons.Repeat,
                contentDescription = stringResource(R.string.nowplaying_repeat),
                turnsOn = nextRepeat != RepeatMode.Off,
                stateDescription =
                    stringResource(
                        when (state.repeatMode) {
                            RepeatMode.Off -> R.string.nowplaying_repeat_off
                            RepeatMode.All -> R.string.nowplaying_repeat_all
                            RepeatMode.One -> R.string.nowplaying_repeat_one
                        },
                    ),
            )
        },
        trailing = {
            MqIconToggle(
                checked = state.shuffle,
                onClick = { onAction(NowPlayingAction.SetShuffle(!state.shuffle)) },
                icon = MqIcons.Shuffle,
                contentDescription = stringResource(R.string.nowplaying_shuffle),
            )
        },
    )
}

/** Queue, Lyrics, Equalizer: the bottom bar in portrait, beside the close button in landscape. */
@Composable
private fun BottomBarButtons(
    state: NowPlayingUiState.Playing,
    onAction: (NowPlayingAction) -> Unit,
    navigation: NowPlayingNavigation,
) {
    IconButton(onClick = navigation.onOpenQueue) {
        Icon(MqIcons.QueueMusic, contentDescription = stringResource(R.string.nowplaying_queue_open))
    }
    MqIconToggle(
        checked = state.lyricsShown,
        onClick = { onAction(NowPlayingAction.ToggleLyrics) },
        icon = MqIcons.Lyrics,
        contentDescription = stringResource(R.string.nowplaying_lyrics),
    )
    IconButton(onClick = navigation.onOpenEqualizer) {
        Icon(MqIcons.GraphicEq, contentDescription = stringResource(R.string.nowplaying_equalizer))
    }
}

/** Bit-perfect USB is on for this track: its sample rate, and that the phone volume does nothing. */
@Composable
private fun BitPerfectBadge(sampleRateHz: Int) {
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(R.string.nowplaying_bit_perfect_badge, formatFrequency(sampleRateHz)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = MediatheqSpacing.m, vertical = MediatheqSpacing.xs),
            )
        }
        Text(
            stringResource(R.string.nowplaying_bit_perfect_volume),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Why Atmos is not playing: the device and output cannot play it, so the server sent a stereo version. */
@Composable
private fun AtmosConvertedNote() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MqIcons.FormatDolbyAtmos, contentDescription = null, Modifier.size(MediatheqSizes.formatBadge), color)
            Text(stringResource(R.string.nowplaying_atmos_unavailable), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            stringResource(R.string.nowplaying_atmos_stereo),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A horizontal swipe over a third of the width (or a fling) plays the next track (to the left) or the previous one
 * (to the right), with a haptic tick when it does; TalkBack gets the same as custom actions. Nothing when not [enabled]
 * (Settings -> Now playing screens, #126).
 */
@Composable
internal fun Modifier.swipeToChangeTrack(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onAction: (NowPlayingAction) -> Unit,
    enabled: Boolean = true,
    /** Put the TalkBack actions on this node; off when a child node carries them (the cover). */
    accessibilityActions: Boolean = true,
): Modifier {
    if (!enabled) return this
    val actions = trackSwipeActions(hasPrevious, hasNext, onAction)
    var width by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val flingVelocity = flingVelocityPx()
    val haptics = LocalHapticFeedback.current
    return then(if (accessibilityActions) Modifier.semantics { customActions = actions } else Modifier)
        .onSizeChanged { width = it.width }
        .draggable(
            state = rememberDraggableState { dragged += it },
            orientation = Orientation.Horizontal,
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity ->
                val toNext = velocity < -flingVelocity || dragged < -width / 3f
                val toPrevious = velocity > flingVelocity || dragged > width / 3f
                if (toNext && hasNext) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    onAction(NowPlayingAction.Next)
                } else if (toPrevious && hasPrevious) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    onAction(NowPlayingAction.Previous)
                }
            },
        )
}

/**
 * Closes on a downward fling, or a drag over a quarter of the screen. Scrolling children (the lyrics, the landscape
 * column) take vertical drags first; what they leave over at their top counts as a pull down too.
 */
@Composable
internal fun Modifier.swipeDownToCollapse(onCollapse: () -> Unit): Modifier {
    var height by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val flingVelocity = flingVelocityPx()
    val collapse by rememberUpdatedState(onCollapse)
    val pulled =
        remember(flingVelocity) {
            object : NestedScrollConnection {
                var distance = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0) distance += available.y
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (available.y > flingVelocity || distance > height / 4f) collapse()
                    distance = 0f
                    return Velocity.Zero
                }
            }
        }
    return onSizeChanged { height = it.height }
        .nestedScroll(pulled)
        .draggable(
            state = rememberDraggableState { dragged += it },
            orientation = Orientation.Vertical,
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity ->
                if (velocity > flingVelocity || dragged > height / 4f) onCollapse()
            },
        )
}

/** The speed that counts as a fling, in pixels per second (about 600 dp/s on any screen density). */
@Composable
internal fun flingVelocityPx(): Float = FLING_DP_PER_SECOND * LocalDensity.current.density

private const val FLING_DP_PER_SECOND = 600f

/** False when the system's animator duration scale is 0 ("Remove animations"): non-essential motion stays still. */
@Composable
private fun animationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

private val PlayerProgress.waveformStyle: MqWaveformStyle
    get() =
        when (this) {
            PlayerProgress.WaveformSmooth -> MqWaveformStyle.Smooth
            PlayerProgress.WaveformBars -> MqWaveformStyle.Bars
            PlayerProgress.WaveformBarsReflected -> MqWaveformStyle.BarsReflected
            PlayerProgress.WaveformSharp, PlayerProgress.Basic, PlayerProgress.Wavy -> MqWaveformStyle.Sharp
        }

/** Next / previous as TalkBack actions, for what a sideways swipe does. */
@Composable
private fun trackSwipeActions(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onAction: (NowPlayingAction) -> Unit,
): List<CustomAccessibilityAction> {
    val nextLabel = stringResource(R.string.nowplaying_swipe_next)
    val previousLabel = stringResource(R.string.nowplaying_swipe_previous)
    return listOfNotNull(
        CustomAccessibilityAction(nextLabel) {
            onAction(NowPlayingAction.Next)
            true
        }.takeIf { hasNext },
        CustomAccessibilityAction(previousLabel) {
            onAction(NowPlayingAction.Previous)
            true
        }.takeIf { hasPrevious },
    )
}
