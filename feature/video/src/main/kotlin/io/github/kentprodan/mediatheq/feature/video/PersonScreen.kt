package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.PersonVideos
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.component.plusBottomBars
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.PersonKey
import io.github.kentprodan.mediatheq.core.ui.MqPosterCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** A person's page on the phone (#442), like the TV's (#432): what of the library they are in, read once. */
@HiltViewModel(assistedFactory = PersonViewModel.Factory::class)
class PersonViewModel
    @AssistedInject
    constructor(
        @Assisted key: PersonKey,
        videos: VideoRepository,
    ) : ViewModel() {
        /** `null` while read. */
        val videos: StateFlow<PersonVideos?> =
            flow { emit(videos.personVideos(key.id)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @AssistedFactory
        interface Factory {
            fun create(key: PersonKey): PersonViewModel
        }
    }

@Composable
internal fun PersonRoute(
    key: PersonKey,
    onOpenMovie: (MediaId) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<PersonViewModel, PersonViewModel.Factory>(key = key.toString()) { it.create(key) }
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    PersonScreen(key.name, videos, onOpenMovie, onOpenSeries, onBack)
}

/** The name, then the movies and the series of the library the person is in, as posters. */
@Composable
fun PersonScreen(
    name: String,
    videos: PersonVideos?,
    onOpenMovie: (MediaId) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
    onBack: () -> Unit,
) {
    VideoPageScaffold(onBack) { padding ->
        if (videos == null) {
            MqLoadingIndicator()
            return@VideoPageScaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MediatheqSpacing.l).plusBottomBars(),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    name,
                    Modifier.padding(top = padding.calculateTopPadding()).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (videos.movies.isEmpty() && videos.series.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.video_person_nothing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(videos.movies, key = { it.id.toString() }) { movie ->
                MqPosterCard(
                    movie.title,
                    movie.year?.toString(),
                    movie.artwork,
                    onClick = { onOpenMovie(movie.id) },
                    watched = movie.userData.played,
                )
            }
            items(videos.series, key = { it.id.toString() }) { series ->
                MqPosterCard(
                    series.title,
                    series.year?.toString(),
                    series.artwork,
                    onClick = { onOpenSeries(series.id) },
                    watched = series.userData.played,
                )
            }
        }
    }
}

private const val COLUMNS = 3
