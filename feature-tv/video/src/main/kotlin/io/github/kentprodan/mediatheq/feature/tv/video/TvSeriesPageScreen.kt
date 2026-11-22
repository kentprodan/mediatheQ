package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButtonStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvIconButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvBackdrop
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterAccent
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterCard
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvTitle
import kotlin.time.Duration

@Composable
fun TvSeriesPageRoute(
    id: MediaId,
    onPlay: (MediaId, Duration) -> Unit,
) {
    val viewModel =
        hiltViewModel<TvSeriesPageViewModel, TvSeriesPageViewModel.Factory>(key = id.toString()) { it.create(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSeriesPageScreen(state, viewModel::onAction, onPlay)
}

/**
 * A series' page on TV (#428): the backdrop header with Play or Resume "S2 · E5", From the start, Favorite and More
 * (watched for the season or the series, the no-spoiler switch); the season tabs, the season's episodes as 16:9 cards
 * with the focused one's overview under them. Select plays an episode where it was left; a long press marks it.
 */
@Composable
fun TvSeriesPageScreen(
    state: TvSeriesPageUiState,
    onAction: (TvSeriesPageAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
) {
    // Accent from the poster (#517): the page in its poster's colors.
    MqTvPosterAccent((state as? TvSeriesPageUiState.Content)?.series?.artwork) {
        when (state) {
            TvSeriesPageUiState.Loading -> {
                Unit
            }

            is TvSeriesPageUiState.Error -> {
                MqTvMessage(stringResource(R.string.tv_series_missing), stringResource(R.string.tv_movie_missing_body))
            }

            is TvSeriesPageUiState.Content -> {
                Content(state, onAction, onPlay)
            }
        }
    }
}

@Composable
private fun Content(
    state: TvSeriesPageUiState.Content,
    onAction: (TvSeriesPageAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
) {
    val series = state.series
    // By id, read from the current episodes: a season switch or a watched change never shows a stale overview.
    var focusedId by remember { mutableStateOf<MediaId?>(null) }
    val focused = state.episodes.firstOrNull { it.id == focusedId }
    MqTvBackdrop(series.backdrop, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = EDGE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MqTvTitle(series.title, series.logo, maxLines = 1)
                val facts =
                    listOfNotNull(
                        series.year?.toString(),
                        series.communityRating?.let {
                            "★ " +
                                String.format(LocalConfiguration.current.locales[0], "%.1f", it)
                        },
                        series.genres
                            .take(3)
                            .joinToString(", ")
                            .ifEmpty { null },
                    ).joinToString(" · ")
                if (facts.isNotEmpty()) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                series.overview?.let {
                    Text(
                        it,
                        Modifier.widthIn(max = 720.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Buttons(state, onAction, onPlay)
            }
            if (state.seasons.size > 1) Seasons(state, onAction)
            Episodes(state, onAction, onPlay) { focusedId = it.id }
            focused?.let { episode ->
                val hidden = state.hideUnwatchedOverviews && !episode.userData.played && episode.overview != null
                Text(
                    if (hidden) stringResource(R.string.tv_series_overview_hidden) else episode.overview.orEmpty(),
                    Modifier.padding(horizontal = EDGE).widthIn(max = 960.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    state.confirm?.let { target -> ConfirmWatched(target, onAction) }
}

@Composable
private fun Buttons(
    state: TvSeriesPageUiState.Content,
    onAction: (TvSeriesPageAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
) {
    val play = remember { FocusRequester() }
    var more by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val next = state.nextUp
        if (next != null) {
            val resume = next.resumePosition()
            val code = episodeCode(next)
            MqTvButton(
                stringResource(playLabel(resume), code),
                { onPlay(next.id, resume) },
                Modifier.focusRequester(play),
                icon = if (resume > Duration.ZERO) MqTvIcons.Resume else MqTvIcons.Play,
            )
        }
        state.firstEpisode?.let { first ->
            MqTvButton(
                stringResource(R.string.tv_movie_from_start),
                { onPlay(first.id, Duration.ZERO) },
                if (next == null) Modifier.focusRequester(play) else Modifier,
                // The primary action when there is no next episode (#519).
                style = if (next == null) MqTvButtonStyle.Filled else MqTvButtonStyle.Outlined,
            )
        }
        MqTvIconButton(
            if (state.series.userData.isFavorite) MqTvIcons.FavoriteFilled else MqTvIcons.Favorite,
            stringResource(
                choose(state.series.userData.isFavorite, R.string.tv_movie_favorite_remove, R.string.tv_movie_favorite),
            ),
            { onAction(TvSeriesPageAction.ToggleFavorite) },
        )
        MqTvIconButton(MqTvIcons.More, stringResource(R.string.tv_series_more), { more = true })
    }
    if (more) MoreMenu(state, onAction) { more = false }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

@Composable
private fun MoreMenu(
    state: TvSeriesPageUiState.Content,
    onAction: (TvSeriesPageAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val season = state.season
    val seasonWatched = state.episodes.isNotEmpty() && state.episodes.all { it.userData.played }
    val seriesWatched = state.series.userData.played
    val entries =
        listOfNotNull(
            season?.let {
                MqTvMenuEntry(
                    stringResource(
                        if (seasonWatched) R.string.tv_series_season_unwatched else R.string.tv_series_season_watched,
                        it.title,
                    ),
                    MqTvIcons.Watched,
                ) { onAction(TvSeriesPageAction.AskWatched(TvWatchedTarget.Season(it, !seasonWatched))) }
            },
            MqTvMenuEntry(
                stringResource(
                    choose(seriesWatched, R.string.tv_series_series_unwatched, R.string.tv_series_series_watched),
                ),
                MqTvIcons.Watched,
            ) { onAction(TvSeriesPageAction.AskWatched(TvWatchedTarget.Series(!seriesWatched))) },
            MqTvMenuEntry(
                stringResource(
                    choose(
                        state.hideUnwatchedOverviews,
                        R.string.tv_series_show_overviews,
                        R.string.tv_series_hide_overviews,
                    ),
                ),
                null,
            ) { onAction(TvSeriesPageAction.ToggleHideOverviews) },
        )
    MqTvMenu(state.series.title, entries, onDismiss)
}

@Composable
private fun ConfirmWatched(
    target: TvWatchedTarget,
    onAction: (TvSeriesPageAction) -> Unit,
) {
    val question =
        when (target) {
            is TvWatchedTarget.Season -> {
                stringResource(
                    if (target.watched) {
                        R.string.tv_series_confirm_season_watched
                    } else {
                        R.string.tv_series_confirm_season_unwatched
                    },
                    target.season.title,
                )
            }

            is TvWatchedTarget.Series -> {
                stringResource(
                    if (target.watched) {
                        R.string.tv_series_confirm_series_watched
                    } else {
                        R.string.tv_series_confirm_series_unwatched
                    },
                )
            }
        }
    MqTvMenu(
        question,
        listOf(
            MqTvMenuEntry(stringResource(R.string.tv_series_confirm_yes), null) {
                onAction(TvSeriesPageAction.ConfirmWatched)
            },
            MqTvMenuEntry(stringResource(R.string.tv_series_confirm_cancel), null) {
                onAction(TvSeriesPageAction.Dismiss)
            },
        ),
        onDismiss = { onAction(TvSeriesPageAction.Dismiss) },
    )
}

@Composable
private fun Seasons(
    state: TvSeriesPageUiState.Content,
    onAction: (TvSeriesPageAction) -> Unit,
) {
    val selected = state.seasons.indexOfFirst { it.id == state.season?.id }.coerceAtLeast(0)
    val requesters = remember(state.seasons.size) { List(state.seasons.size) { FocusRequester() } }
    // Into the tabs from above or below: the selected season's tab, never the nearest one (it would select itself).
    TabRow(
        selected,
        Modifier
            .padding(start = EDGE, top = 8.dp)
            .focusProperties { onEnter = { requesters.getOrNull(selected)?.requestFocus() } },
    ) {
        state.seasons.forEachIndexed { i, season ->
            Tab(
                selected = i == selected,
                modifier = Modifier.focusRequester(requesters[i]),
                onFocus = { if (i != selected) onAction(TvSeriesPageAction.SelectSeason(season.id)) },
                colors =
                    TabDefaults.pillIndicatorTabColors(
                        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text(
                    if (season.number == 0) stringResource(R.string.tv_series_specials) else season.title,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun Episodes(
    state: TvSeriesPageUiState.Content,
    onAction: (TvSeriesPageAction) -> Unit,
    onPlay: (MediaId, Duration) -> Unit,
    onFocused: (Video) -> Unit,
) {
    val row = rememberLazyListState()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    // Opens on Next up when it is in this season, else on its first episode (the row keeps no index across seasons).
    LaunchedEffect(state.season?.id) {
        row.scrollToItem(state.episodes.indexOfFirst { it.id == state.nextUp?.id }.coerceAtLeast(0))
    }
    LazyRow(
        Modifier.heightIn(min = 200.dp),
        state = row,
        contentPadding = PaddingValues(horizontal = EDGE, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.episodes, key = { it.id.toString() }, contentType = { "episode" }) { episode ->
            val resume = episode.resumePosition()
            MqTvPosterCard(
                listOfNotNull(episode.episodeNumber?.let { "E$it" }, episode.title).joinToString(" · "),
                episode.artwork,
                onClick = { onPlay(episode.id, resume) },
                modifier = Modifier.width(EPISODE_WIDTH).onFocusChanged { if (it.isFocused) onFocused(episode) },
                subtitle = episode.duration.takeIf { it.isPositive() }?.let { runtimeLabel(it) },
                watched = episode.userData.played,
                progress = episode.userData.playedPercentage?.let { (it / 100).toFloat() },
                placeholder = MqTvIcons.Series,
                aspectRatio = 16f / 9f,
                onLongClick = { menuFor = episode },
            )
        }
    }
    menuFor?.let { episode ->
        MqTvMenu(
            episode.title,
            listOf(
                MqTvMenuEntry(
                    stringResource(
                        choose(episode.userData.played, R.string.tv_movie_unwatched, R.string.tv_movie_watched),
                    ),
                    MqTvIcons.Watched,
                ) { onAction(TvSeriesPageAction.ToggleEpisodeWatched(episode)) },
            ),
        ) { menuFor = null }
    }
}

@Composable
private fun runtimeLabel(duration: Duration) =
    duration.toComponents { hours, minutes, _, _ ->
        if (hours > 0) {
            stringResource(R.string.tv_movie_runtime_hours, hours, minutes)
        } else {
            stringResource(R.string.tv_movie_runtime_minutes, minutes)
        }
    }

/** "S2 · E5", or the title when the numbers are unknown. */
internal fun episodeCode(episode: Video): String {
    val season = episode.seasonNumber
    val number = episode.episodeNumber
    return if (season != null && number != null) "S$season · E$number" else episode.title
}

private fun playLabel(resume: Duration) =
    if (resume >
        Duration.ZERO
    ) {
        R.string.tv_series_resume
    } else {
        R.string.tv_series_play
    }

/** [whenTrue] or [whenFalse]: a label that flips with a state. */
private fun choose(
    on: Boolean,
    whenTrue: Int,
    whenFalse: Int,
) = if (on) whenTrue else whenFalse

private val EDGE = 48.dp
private val EPISODE_WIDTH = 216.dp
