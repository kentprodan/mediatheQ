package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.SMART_EPISODES
import io.github.kentprodan.mediatheq.core.data.VideoDownload
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.MqEpisodeRow
import io.github.kentprodan.mediatheq.core.ui.MqTracksSheet
import io.github.kentprodan.mediatheq.core.ui.RemoveDownloadMenuItem
import kotlin.time.Duration

@Composable
internal fun SeriesRoute(
    id: MediaId,
    onPlay: (MediaId, Duration) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            key = id.toString(),
            creationCallback = { it.create(id) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SeriesScreen(state, viewModel::onAction, onPlay, onBack)
}

/**
 * A series' page (#273): the backdrop, title, year and Favorite, the overview, a Next up card, the seasons as tabs and
 * the selected season's episodes. A tap on an episode plays it from where it was left; its menu (also a long press)
 * offers Play from start and Watched. Stateless.
 */
@Composable
fun SeriesScreen(
    state: SeriesUiState,
    onAction: (SeriesAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
    onBack: () -> Unit,
) {
    VideoPageScaffold(onBack) { padding ->
        when (state) {
            SeriesUiState.Loading -> MqLoadingIndicator()
            is SeriesUiState.Error -> VideoPageError(state.error, onBack, Modifier.padding(padding))
            is SeriesUiState.Content -> SeriesContent(state, onAction, onPlay)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeriesContent(
    state: SeriesUiState.Content,
    onAction: (SeriesAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
) {
    state.tracksOf?.let { (_, tracks) ->
        MqTracksSheet(
            tracks.sheet(stringResource(R.string.video_tracks_off), stringResource(R.string.video_track)),
            onAudio = { onAction(SeriesAction.ChooseAudio(it)) },
            onSubtitle = { onAction(SeriesAction.ChooseSubtitle(it)) },
            onWholeSeason = { onAction(SeriesAction.WholeSeason(it)) },
            onDismiss = { onAction(SeriesAction.CloseTracks) },
        )
    }
    val series = state.series
    val play = { episode: Video -> onPlay(episode.id, episode.resumePosition()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = MediatheqSpacing.xl)) {
        item(key = "backdrop") {
            MqCover(
                series.backdrop ?: series.artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                aspectRatio = 16 / 9f,
            )
        }
        item(key = "header") {
            Row(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        series.title,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        modifier = Modifier.semantics { heading() },
                    )
                    series.year?.let {
                        Text(
                            it.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MqFavoriteToggle(series.userData.isFavorite, { onAction(SeriesAction.ToggleFavorite) })
            }
        }
        item(key = "follow") {
            // Smart downloads (#383): the next episodes stay on the phone while the series is followed.
            FilterChip(
                selected = state.followed,
                onClick = { onAction(SeriesAction.ToggleFollow) },
                label = { Text(stringResource(R.string.video_follow, SMART_EPISODES)) },
                leadingIcon = {
                    Icon(if (state.followed) MqIcons.Check else MqIcons.Download, contentDescription = null)
                },
                modifier = Modifier.padding(horizontal = MediatheqSpacing.l),
            )
        }
        series.overview?.let { overview ->
            item(key = "overview") {
                Text(
                    overview,
                    Modifier.padding(horizontal = MediatheqSpacing.l),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        state.nextUp?.let { next ->
            item(key = "next") {
                // Not clickable itself: the row inside is the one tap target (TalkBack stops once).
                Card(
                    modifier = Modifier.fillMaxWidth().padding(MediatheqSpacing.l),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        stringResource(R.string.video_next_up),
                        Modifier.padding(start = MediatheqSpacing.l, top = MediatheqSpacing.m),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    MqEpisodeRow(next, onClick = { play(next) })
                }
            }
        }
        if (state.seasons.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.video_series_empty_episodes),
                    Modifier.padding(MediatheqSpacing.l),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "seasons") {
                val selected = state.seasons.indexOf(state.season).coerceAtLeast(0)
                PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = MediatheqSpacing.l) {
                    state.seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = index == selected,
                            onClick = { onAction(SeriesAction.SelectSeason(season.id)) },
                            text = { Text(season.title) },
                        )
                    }
                }
            }
            item(key = "season_download") {
                // The selected season's download (#279); no series-level download.
                TextButton(
                    onClick = { onAction(SeriesAction.ToggleSeasonDownload) },
                    modifier = Modifier.padding(horizontal = MediatheqSpacing.s),
                ) {
                    val (icon, label) =
                        if (state.seasonDownloaded) {
                            MqIcons.Delete to R.string.video_remove_season_download
                        } else {
                            MqIcons.Download to R.string.video_download_season
                        }
                    Icon(icon, contentDescription = null)
                    Text(stringResource(label), Modifier.padding(start = MediatheqSpacing.s))
                }
            }
            items(state.episodes, key = { it.id.toString() }) { episode ->
                EpisodeWithMenu(
                    episode,
                    download = state.downloads[episode.id],
                    onDownload = { onAction(SeriesAction.ToggleEpisodeDownload(episode)) },
                    onPlay = { play(episode) },
                    onPlayFromStart = { onPlay(episode.id, Duration.ZERO) },
                    onTracks = { onAction(SeriesAction.OpenTracks(episode)) },
                    overview = state.overviewOf(episode),
                ) {
                    onAction(SeriesAction.ToggleWatched(episode))
                }
            }
        }
    }
}

/** An episode row with its menu: Play from start, and Watched or Not watched. */
@Composable
private fun EpisodeWithMenu(
    episode: Video,
    download: VideoDownload?,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onTracks: () -> Unit,
    overview: String?,
    onToggleWatched: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    MqEpisodeRow(
        episode,
        onClick = onPlay,
        overview = overview,
        onLongClick = { menu = true },
        downloaded = download?.state == DownloadState.Completed,
    ) {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(MqIcons.MoreVert, contentDescription = stringResource(R.string.video_episode_menu, episode.title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.video_play_from_start)) },
                    onClick = {
                        menu = false
                        onPlayFromStart()
                    },
                )
                // On its own (#279): one kept only by its season shows Download, adding the single origin.
                if (download?.origins?.contains(DownloadOrigin.Single) == true) {
                    RemoveDownloadMenuItem(onDownload) { menu = false }
                } else {
                    DownloadMenuItem(onDownload) { menu = false }
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.video_tracks_open)) },
                    onClick = {
                        menu = false
                        onTracks()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (episode.userData.played) {
                                    R.string.video_mark_unwatched
                                } else {
                                    R.string.video_mark_as_watched
                                },
                            ),
                        )
                    },
                    onClick = {
                        menu = false
                        onToggleWatched()
                    },
                )
            }
        }
    }
}
