package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** A movie's page (#273): its facts from Room, Favorite and Mark watched through the optimistic user data writes. */
@HiltViewModel(assistedFactory = MovieViewModel.Factory::class)
class MovieViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: MediaId,
        videos: VideoRepository,
        private val userData: UserDataRepository,
    ) : ViewModel() {
        val uiState: StateFlow<MovieUiState> =
            combine(videos.video(id), videos.streams(id)) { movie, streams ->
                if (movie == null) {
                    MovieUiState.Error(MqError.NotFound)
                } else {
                    MovieUiState.Content(
                        movie = movie,
                        format = formatLine(movie, streams),
                        externalSubtitles = externalSubtitles(streams),
                        resumeAt = movie.resumePosition().takeIf { it > Duration.ZERO },
                    )
                }
            }.catch { emit(MovieUiState.Error(MqError.Storage)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovieUiState.Loading)

        fun onAction(action: MovieAction) {
            val movie = (uiState.value as? MovieUiState.Content)?.movie ?: return
            viewModelScope.launch {
                when (action) {
                    MovieAction.ToggleFavorite -> userData.setFavorite(id, !movie.userData.isFavorite)
                    MovieAction.ToggleWatched -> userData.setPlayed(id, !movie.userData.played)
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): MovieViewModel
        }
    }
