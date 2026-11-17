package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.data.formatLine
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Person
import io.github.kentprodan.mediatheq.core.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * A movie's page on TV (#427): its facts from Room, Favorite and Watched through the optimistic user data writes, and
 * the audio and subtitle choice kept for the movie (the phone's TrackPicker, ADR 0050).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TvMovieViewModel.Factory::class)
class TvMovieViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: MediaId,
        videos: VideoRepository,
        private val userData: UserDataRepository,
        private val picker: TrackPicker,
    ) : ViewModel() {
        // From the server once per page (#427): who is in it, and what in the library is like it.
        private val related =
            flow { emit(videos.people(id) to videos.similarMovies(id, SIMILAR_LIMIT)) }
                .onStart { emit(emptyList<Person>() to emptyList()) }

        val uiState: StateFlow<TvMovieUiState> =
            combine(
                videos.video(id),
                videos.streams(id),
                videos.video(id).flatMapLatest { it?.let(picker::tracks) ?: flowOf(null) },
                related,
            ) { movie, streams, tracks, (people, similar) ->
                if (movie == null) {
                    TvMovieUiState.Error(MqError.NotFound)
                } else {
                    TvMovieUiState.Content(
                        movie = movie,
                        format = formatLine(movie, streams),
                        resumeAt = movie.resumePosition().takeIf { it > Duration.ZERO },
                        tracks = tracks,
                        people = people,
                        similar = similar,
                    )
                }
            }.catch { emit(TvMovieUiState.Error(MqError.Storage)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvMovieUiState.Loading)

        fun onAction(action: TvMovieAction) {
            val content = uiState.value as? TvMovieUiState.Content ?: return
            val movie = content.movie
            val resolved = content.tracks?.resolved
            viewModelScope.launch {
                when (action) {
                    TvMovieAction.ToggleFavorite -> {
                        userData.setFavorite(id, !movie.userData.isFavorite)
                    }

                    TvMovieAction.ToggleWatched -> {
                        userData.setPlayed(id, !movie.userData.played)
                    }

                    is TvMovieAction.ChooseAudio -> {
                        picker.choose(movie, action.index, resolved?.subtitleIndex, wholeSeason = false)
                    }

                    is TvMovieAction.ChooseSubtitle -> {
                        picker.choose(
                            movie,
                            resolved?.audioIndex,
                            action.index,
                            wholeSeason = false,
                            action.index == null,
                        )
                    }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(id: MediaId): TvMovieViewModel
        }

        private companion object {
            const val SIMILAR_LIMIT = 12
        }
    }

sealed interface TvMovieUiState {
    data object Loading : TvMovieUiState

    data class Content(
        val movie: Video,
        /** "4K · HEVC · HDR10 · 5.1", `null` when unknown. */
        val format: String?,
        /** Where Resume starts; `null` shows only Play (never started, or watched). */
        val resumeAt: Duration?,
        /** Its audio and subtitle streams and what will play; `null` while read. */
        val tracks: VideoTracks? = null,
        /** Cast and crew, leads first; empty offline or while read. */
        val people: List<Person> = emptyList(),
        /** Movies in the library like this one. */
        val similar: List<Video> = emptyList(),
    ) : TvMovieUiState

    data class Error(
        val error: MqError,
    ) : TvMovieUiState
}

sealed interface TvMovieAction {
    data object ToggleFavorite : TvMovieAction

    data object ToggleWatched : TvMovieAction

    data class ChooseAudio(
        val index: Int,
    ) : TvMovieAction

    /** `null` = subtitles off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : TvMovieAction
}
