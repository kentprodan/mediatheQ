package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.MoviesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesListKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey

internal val MovieTexts =
    VideoGridTexts(
        R.string.video_movies,
        R.string.video_movies_empty_title,
        R.string.video_empty_body,
    ) { MqIcons.Movie }
internal val SeriesTexts =
    VideoGridTexts(R.string.video_series, R.string.video_series_empty_title, R.string.video_empty_body) { MqIcons.Tv }

/** Registers the Movies and Series grids (#270), the movie and series pages (#273) and the video player (#272). */
fun EntryProviderScope<NavKey>.videoEntry(navigator: Navigator) {
    entry<MoviesKey> {
        val viewModel: MoviesViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        VideoGridScreen(MovieTexts, state, viewModel::onAction, { navigator.navigateTo(MovieKey(it)) }, navigator::back)
    }
    entry<SeriesListKey> {
        val viewModel: SeriesListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        VideoGridScreen(
            SeriesTexts,
            state,
            viewModel::onAction,
            { navigator.navigateTo(SeriesKey(it)) },
            navigator::back,
        )
    }
    entry<MovieKey> { key ->
        MovieRoute(
            key.id,
            onPlay = { navigator.navigateTo(VideoPlayerKey(key.id, it.inWholeMilliseconds)) },
            onBack = navigator::back,
        )
    }
    entry<SeriesKey> { key ->
        SeriesRoute(
            key.id,
            onPlay = { episode, start -> navigator.navigateTo(VideoPlayerKey(episode, start.inWholeMilliseconds)) },
            onBack = navigator::back,
        )
    }
    entry<VideoPlayerKey> { key -> VideoPlayerRoute(key, navigator::back) }
}
