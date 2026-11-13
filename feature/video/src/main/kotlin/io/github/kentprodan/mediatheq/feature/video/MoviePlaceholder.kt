package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.formatDuration
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration

/** The movie a placeholder page shows (until #273): its title and resume point, for Play and Resume. */
@HiltViewModel(assistedFactory = MoviePlaceholderViewModel.Factory::class)
class MoviePlaceholderViewModel
    @AssistedInject
    constructor(
        @Assisted id: MediaId,
        videos: VideoRepository,
    ) : ViewModel() {
        val movie: StateFlow<Video?> =
            videos
                .video(
                    id,
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): MoviePlaceholderViewModel
        }
    }

@Composable
internal fun MoviePlaceholderRoute(
    id: MediaId,
    onPlay: (Duration) -> Unit,
    onBack: () -> Unit,
    viewModel: MoviePlaceholderViewModel =
        hiltViewModel<MoviePlaceholderViewModel, MoviePlaceholderViewModel.Factory>(
            key = id.toString(),
            creationCallback = { it.create(id) },
        ),
) {
    val movie by viewModel.movie.collectAsStateWithLifecycle()
    MoviePlaceholder(movie, onPlay, onBack)
}

/**
 * A movie's page before #273: its title, Play and, with a resume point, Resume, so a movie can be watched already.
 * Without the movie (still read, or gone) only the "coming soon" text shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePlaceholder(
    movie: Video?,
    onPlay: (Duration) -> Unit,
    onBack: () -> Unit,
) {
    if (movie == null) {
        ComingSoon(onBack)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.video_back))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    movie.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                movie.subtitle()?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                val resume = movie.userData.playbackPosition
                Button(onClick = { onPlay(Duration.ZERO) }) {
                    Icon(MqIcons.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.video_play))
                }
                if (resume > Duration.ZERO) {
                    FilledTonalButton(onClick = { onPlay(resume) }) {
                        Text(stringResource(R.string.video_resume) + " · " + formatDuration(resume))
                    }
                }
                Text(
                    stringResource(R.string.video_coming_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
