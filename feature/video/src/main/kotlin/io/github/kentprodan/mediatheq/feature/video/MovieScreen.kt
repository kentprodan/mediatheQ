package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqIconToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.ui.MqTracksSheet
import kotlin.time.Duration

@Composable
internal fun MovieRoute(
    id: MediaId,
    onPlay: (Duration) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieViewModel =
        hiltViewModel<MovieViewModel, MovieViewModel.Factory>(
            key = id.toString(),
            creationCallback = { it.create(id) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MovieScreen(state, viewModel::onAction, onPlay, onBack)
}

/**
 * A movie's page (#273): the backdrop, the poster beside title, year, runtime and rating, the format line, Play or
 * Resume, Favorite and Watched, then the tagline, the overview and the genres. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieScreen(
    state: MovieUiState,
    onAction: (MovieAction) -> Unit,
    onPlay: (Duration) -> Unit,
    onBack: () -> Unit,
) {
    VideoPageScaffold(onBack) { padding ->
        when (state) {
            MovieUiState.Loading -> {
                MqLoadingIndicator()
            }

            is MovieUiState.Error -> {
                VideoPageError(state.error, onBack, Modifier.padding(padding))
            }

            is MovieUiState.Content -> {
                MovieContent(state, onAction, onPlay)
            }
        }
    }
}

/** The pages' frame: a transparent top bar with Back over the backdrop, which runs under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPageScaffold(
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.video_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding -> content(padding) }
}

/** Not in the library, or Room could not be read. */
@Composable
internal fun VideoPageError(
    error: MqError,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        MqEmptyState(
            MqIcons.Movie,
            stringResource(R.string.video_page_error_title),
            body =
                stringResource(
                    if (error ==
                        MqError.NotFound
                    ) {
                        R.string.video_player_not_found_body
                    } else {
                        R.string.video_page_error_body
                    },
                ),
            actionLabel = stringResource(R.string.video_back),
            onAction = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MovieContent(
    state: MovieUiState.Content,
    onAction: (MovieAction) -> Unit,
    onPlay: (Duration) -> Unit,
) {
    val movie = state.movie
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.xl)) {
        item(key = "backdrop") {
            MqCover(
                movie.backdrop ?: movie.artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                aspectRatio = 16 / 9f,
            )
        }
        item(key = "header") {
            Row(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                MqCover(
                    movie.artwork,
                    contentDescription = null,
                    modifier = Modifier.width(112.dp),
                    aspectRatio =
                        2 / 3f,
                )
                Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                    Text(
                        movie.title,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        movie.facts(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.format?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.externalSubtitles > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.video_external_subtitles,
                                state.externalSubtitles,
                                state.externalSubtitles,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "buttons") {
            FlowRow(
                Modifier.padding(horizontal = MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PlayButtons(state.resumeAt, onPlay)
                MqFavoriteToggle(movie.userData.isFavorite, { onAction(MovieAction.ToggleFavorite) })
                WatchedToggle(movie.userData.played) { onAction(MovieAction.ToggleWatched) }
            }
        }
        state.tracks?.let { tracks ->
            item(key = "tracks") {
                TracksRow(
                    tracks,
                    onAudio = { onAction(MovieAction.ChooseAudio(it)) },
                ) { onAction(MovieAction.ChooseSubtitle(it)) }
            }
        }
        item(key = "text") {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                movie.tagline?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                movie.overview?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (movie.genres.isNotEmpty()) {
                    Text(
                        movie.genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * "Audio and subtitles" with what will play ("English · Romanian"), opening the tracks sheet (#274); a pick is saved
 * for this video.
 */
@Composable
internal fun TracksRow(
    tracks: VideoTracks,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val sheet = tracks.sheet(stringResource(R.string.video_tracks_off), stringResource(R.string.video_track))
    ListItem(
        headlineContent = { Text(stringResource(R.string.video_tracks_open)) },
        supportingContent = {
            Text(
                listOfNotNull(
                    sheet.audio.firstOrNull { it.selected }?.label,
                    sheet.subtitles.firstOrNull { it.selected }?.label,
                ).joinToString(" · "),
            )
        },
        leadingContent = { Icon(MqIcons.Subtitles, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        MqTracksSheet(sheet, onAudio, onSubtitle, onWholeSeason = {}, onDismiss = { open = false })
    }
}

/** Resume at [resumeAt] and Play from start, or only Play when there is nothing to resume. */
@Composable
internal fun PlayButtons(
    resumeAt: Duration?,
    onPlay: (Duration) -> Unit,
) {
    if (resumeAt != null) {
        Button(onClick = { onPlay(resumeAt) }) {
            Icon(MqIcons.PlayArrow, contentDescription = null)
            Text(stringResource(R.string.video_resume_at, formatDuration(resumeAt)))
        }
        FilledTonalButton(onClick = { onPlay(Duration.ZERO) }) { Text(stringResource(R.string.video_play_from_start)) }
    } else {
        Button(onClick = { onPlay(Duration.ZERO) }) {
            Icon(MqIcons.PlayArrow, contentDescription = null)
            Text(stringResource(R.string.video_play))
        }
    }
}

/** The watched mark as a toggle: a check that fills when watched. */
@Composable
internal fun WatchedToggle(
    watched: Boolean,
    onToggle: () -> Unit,
) {
    MqIconToggle(
        checked = watched,
        onClick = onToggle,
        icon = MqIcons.Check,
        contentDescription = stringResource(R.string.video_mark_watched),
        stateDescription = stringResource(if (watched) R.string.video_watched else R.string.video_not_watched),
    )
}

/** "2019 · 1:38:00 · PG-13" from what is known. */
internal fun Video.facts(): String =
    listOfNotNull(
        year?.toString(),
        duration.takeIf { it > Duration.ZERO }?.let(::formatDuration),
        officialRating,
    ).joinToString(" · ")
