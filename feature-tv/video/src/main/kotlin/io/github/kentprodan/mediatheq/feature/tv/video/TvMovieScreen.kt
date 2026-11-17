package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Person
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAlbumCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvBackdrop
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterCard
import kotlin.time.Duration

@Composable
fun TvMovieRoute(
    id: MediaId,
    onOpenMovie: (MediaId) -> Unit = {},
    onPlay: (MediaId, Duration) -> Unit,
) {
    val viewModel = hiltViewModel<TvMovieViewModel, TvMovieViewModel.Factory>(key = id.toString()) { it.create(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvMovieScreen(state, viewModel::onAction, onPlay, onOpenMovie)
}

/**
 * A movie's page on TV (#427): its backdrop over the whole screen, dimmed toward the start and the bottom, the title,
 * the facts, the format and the overview, then Play (or Resume and From the start), Favorite, Watched and Audio &
 * subtitles. Play takes the focus.
 */
@Composable
fun TvMovieScreen(
    state: TvMovieUiState,
    onAction: (TvMovieAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
    onOpenMovie: (MediaId) -> Unit = {},
) {
    when (state) {
        TvMovieUiState.Loading -> {
            Unit
        }

        is TvMovieUiState.Error -> {
            MqTvMessage(stringResource(R.string.tv_movie_missing), stringResource(R.string.tv_movie_missing_body))
        }

        is TvMovieUiState.Content -> {
            val movie = state.movie
            MqTvBackdrop(movie.backdrop, Modifier.fillMaxSize()) {
                // Scrolls down to the rows below the buttons; D-pad focus brings each into view.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        movie.title,
                        Modifier.widthIn(max = 840.dp).semantics { heading() },
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    facts(movie).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.format?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    movie.overview?.let {
                        Text(
                            it,
                            Modifier.widthIn(max = 720.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Buttons(state, onAction, onPlay, Modifier.padding(top = 12.dp))
                    if (state.people.isNotEmpty()) PeopleRow(state.people)
                    if (state.similar.isNotEmpty()) SimilarRow(state.similar, onOpenMovie)
                }
            }
        }
    }
}

@Composable
private fun Buttons(
    state: TvMovieUiState.Content,
    onAction: (TvMovieAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
    modifier: Modifier,
) {
    val movie = state.movie
    val play = remember { FocusRequester() }
    var tracks by remember { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val resume = state.resumeAt
        if (resume != null) {
            MqTvButton(
                stringResource(R.string.tv_movie_resume, formatDuration(resume)),
                { onPlay(movie.id, resume) },
                Modifier.focusRequester(play),
                icon = MqTvIcons.Resume,
            )
            MqTvButton(stringResource(R.string.tv_movie_from_start), { onPlay(movie.id, Duration.ZERO) })
        } else {
            MqTvButton(
                stringResource(R.string.tv_movie_play),
                { onPlay(movie.id, Duration.ZERO) },
                Modifier.focusRequester(play),
                icon = MqTvIcons.Play,
            )
        }
        MqTvButton(
            stringResource(
                if (movie.userData.isFavorite) R.string.tv_movie_favorite_remove else R.string.tv_movie_favorite,
            ),
            { onAction(TvMovieAction.ToggleFavorite) },
            icon = if (movie.userData.isFavorite) MqTvIcons.FavoriteFilled else MqTvIcons.Favorite,
        )
        MqTvButton(
            stringResource(if (movie.userData.played) R.string.tv_movie_unwatched else R.string.tv_movie_watched),
            { onAction(TvMovieAction.ToggleWatched) },
            icon = MqTvIcons.Watched,
        )
        if (state.tracks?.streams?.isNotEmpty() == true) {
            MqTvButton(stringResource(R.string.tv_movie_tracks), { tracks = true })
        }
    }
    state.tracks?.takeIf { tracks }?.let { TracksMenu(it, onAction) { tracks = false } }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

/** The audio streams, then Off and the subtitles; the one that will play is marked. */
@Composable
private fun TracksMenu(
    tracks: VideoTracks,
    onAction: (TvMovieAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val trackFormat = stringResource(R.string.tv_movie_track)
    val current = stringResource(R.string.tv_movie_track_current)

    fun MediaStreamInfo.label() = displayTitle ?: title ?: language ?: trackFormat.format(index)

    fun mark(
        label: String,
        on: Boolean,
    ) = if (on) "$label · $current" else label
    val audio = tracks.streams.filter { it.type == StreamType.Audio }
    val playingAudio =
        tracks.resolved.audioIndex ?: audio.firstOrNull { it.isDefault }?.index ?: audio.firstOrNull()?.index
    val audioLabel = stringResource(R.string.tv_movie_audio)
    val subtitleLabel = stringResource(R.string.tv_movie_subtitles)
    val off = stringResource(R.string.tv_movie_subtitles_off)
    val entries =
        audio.map { stream ->
            MqTvMenuEntry(mark("$audioLabel: ${stream.label()}", stream.index == playingAudio), null) {
                onAction(TvMovieAction.ChooseAudio(stream.index))
            }
        } +
            MqTvMenuEntry(mark("$subtitleLabel: $off", tracks.resolved.subtitleIndex == null), null) {
                onAction(TvMovieAction.ChooseSubtitle(null))
            } +
            tracks.streams.filter { it.type == StreamType.Subtitle }.map { stream ->
                MqTvMenuEntry(
                    mark("$subtitleLabel: ${stream.label()}", stream.index == tracks.resolved.subtitleIndex),
                    null,
                ) {
                    onAction(TvMovieAction.ChooseSubtitle(stream.index))
                }
            }
    MqTvMenu(stringResource(R.string.tv_movie_tracks), entries, onDismiss)
}

/** "2011 · 1 h 40 min · ★ 7.2 · R · Comedy, Drama"; missing parts are left out. */
@Composable
private fun facts(movie: Video): String {
    val runtime =
        movie.duration.takeIf { it.isPositive() }?.toComponents { hours, minutes, _, _ ->
            if (hours > 0) {
                stringResource(R.string.tv_movie_runtime_hours, hours, minutes)
            } else {
                stringResource(R.string.tv_movie_runtime_minutes, minutes)
            }
        }
    val rating = movie.communityRating?.let { "★ " + String.format(LocalConfiguration.current.locales[0], "%.1f", it) }
    val genres =
        movie.genres
            .take(3)
            .joinToString(", ")
            .ifEmpty { null }
    return listOfNotNull(movie.year?.toString(), runtime, rating, movie.officialRating, genres).joinToString(" · ")
}

/** Cast and crew (#427): actors with their character, then the crew with their job. A person page comes with #432. */
@Composable
private fun PeopleRow(people: List<Person>) {
    RowTitle(stringResource(R.string.tv_movie_cast))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(people, key = { it.id.toString() + it.kind + it.role }, contentType = { "person" }) { person ->
            MqTvAlbumCard(
                person.name,
                person.artwork,
                onClick = {},
                modifier = Modifier.width(PERSON_WIDTH),
                subtitle = person.role ?: person.kind,
                placeholder = MqTvIcons.Person,
            )
        }
    }
}

@Composable
private fun SimilarRow(
    movies: List<Video>,
    onOpen: (MediaId) -> Unit,
) {
    RowTitle(stringResource(R.string.tv_movie_similar))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(movies, key = { it.id.toString() }, contentType = { "poster" }) { movie ->
            MqTvPosterCard(
                movie.title,
                movie.artwork,
                onClick = { onOpen(movie.id) },
                modifier = Modifier.width(POSTER_WIDTH),
                subtitle = movie.year?.toString(),
                watched = movie.userData.played,
            )
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        Modifier.padding(top = 24.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}

private val PERSON_WIDTH = 140.dp
private val POSTER_WIDTH = 150.dp
