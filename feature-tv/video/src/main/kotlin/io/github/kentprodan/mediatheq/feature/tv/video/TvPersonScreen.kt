package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.PersonVideos
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMessage
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.PersonKey
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvPosterCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** A person's page on TV (#432): what of the library they are in, read from the server once. */
@HiltViewModel(assistedFactory = TvPersonViewModel.Factory::class)
class TvPersonViewModel
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
            fun create(key: PersonKey): TvPersonViewModel
        }
    }

@Composable
fun TvPersonRoute(
    key: PersonKey,
    onOpenMovie: (MediaId) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
) {
    val viewModel = hiltViewModel<TvPersonViewModel, TvPersonViewModel.Factory>(key = key.toString()) { it.create(key) }
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    TvPersonScreen(key.name, videos, onOpenMovie, onOpenSeries)
}

/** The person's name, then rows of the movies and the series of the library they are in (newest first). */
@Composable
fun TvPersonScreen(
    name: String,
    videos: PersonVideos?,
    onOpenMovie: (MediaId) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                name,
                Modifier.padding(horizontal = EDGE).semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
            )
        }
        if (videos != null && videos.movies.isEmpty() && videos.series.isEmpty()) {
            item {
                MqTvMessage(
                    stringResource(R.string.tv_person_nothing, name),
                    stringResource(R.string.tv_person_nothing_body),
                )
            }
        }
        if (videos?.movies?.isNotEmpty() == true) {
            item {
                Row(stringResource(R.string.tv_video_movies)) {
                    items(videos.movies, key = { it.id.toString() }) { movie ->
                        MqTvPosterCard(
                            movie.title,
                            movie.artwork,
                            onClick = { onOpenMovie(movie.id) },
                            modifier = Modifier.width(POSTER_WIDTH),
                            subtitle = movie.year?.toString(),
                            watched = movie.userData.played,
                        )
                    }
                }
            }
        }
        if (videos?.series?.isNotEmpty() == true) {
            item {
                Row(stringResource(R.string.tv_video_series)) {
                    items(videos.series, key = { it.id.toString() }) { series ->
                        MqTvPosterCard(
                            series.title,
                            series.artwork,
                            onClick = { onOpenSeries(series.id) },
                            modifier = Modifier.width(POSTER_WIDTH),
                            subtitle = series.year?.toString(),
                            placeholder = MqTvIcons.Series,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            Modifier.padding(horizontal = EDGE).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = EDGE, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private val EDGE = 48.dp
private val POSTER_WIDTH = 150.dp
