package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.RemotePlaybackRepository
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.externalSubtitles
import io.github.kentprodan.mediatheq.core.data.formatLine
import io.github.kentprodan.mediatheq.core.data.resumePosition
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RemoteSession
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** A movie's page (#273): its facts from Room, Favorite and Mark watched through the optimistic user data writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MovieViewModel.Factory::class)
class MovieViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: MediaId,
        videos: VideoRepository,
        private val userData: UserDataRepository,
        private val picker: TrackPicker,
        private val downloader: VideoDownloader,
        private val remote: RemotePlaybackRepository? = null,
        private val playback: PlaybackConnection? = null,
    ) : ViewModel() {
        /** The TVs running mediatheQ (#389), read when the page opens; the result of the last send. */
        private val tvs = MutableStateFlow<List<RemoteSession>>(emptyList())
        private val sent = MutableStateFlow<SentToTv?>(null)

        init {
            viewModelScope.launch { tvs.value = remote?.devices().orEmpty() }
        }

        private val tracks = videos.video(id).flatMapLatest { it?.let(picker::tracks) ?: flowOf(null) }

        val uiState: StateFlow<MovieUiState> =
            combine(
                videos.video(id),
                videos.streams(id),
                tracks,
                downloader.states,
                combine(tvs, sent) { list, last -> list to last },
            ) { movie, streams, tracks, downloads, (tvList, last) ->
                if (movie == null) {
                    MovieUiState.Error(MqError.NotFound)
                } else {
                    MovieUiState.Content(
                        movie = movie,
                        format = formatLine(movie, streams),
                        externalSubtitles = externalSubtitles(streams),
                        resumeAt = movie.resumePosition().takeIf { it > Duration.ZERO },
                        tracks = tracks,
                        download = downloads[movie.id],
                        tvs = tvList,
                        sentToTv = last,
                    )
                }
            }.combine(flow { emit(videos.people(id)) }.onStart { emit(emptyList()) }) { state, people ->
                (state as? MovieUiState.Content)?.copy(people = people) ?: state
            }.catch { emit(MovieUiState.Error(MqError.Storage)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovieUiState.Loading)

        fun onAction(action: MovieAction) {
            val content = uiState.value as? MovieUiState.Content ?: return
            val movie = content.movie
            val resolved = content.tracks?.resolved
            viewModelScope.launch {
                when (action) {
                    MovieAction.ToggleFavorite -> {
                        userData.setFavorite(id, !movie.userData.isFavorite)
                    }

                    MovieAction.ToggleWatched -> {
                        userData.setPlayed(id, !movie.userData.played)
                    }

                    // #389: from where the phone is if it plays this movie (and the phone then pauses), else the resume
                    // point; the TV opens it in mediatheQ.
                    is MovieAction.PlayOnTv -> {
                        val here =
                            playback
                                ?.state
                                ?.value
                                ?.item
                                ?.id == id
                        val position =
                            if (here) playback?.position?.value ?: Duration.ZERO else content.resumeAt ?: Duration.ZERO
                        val result = remote?.playOn(action.tv, id, position) ?: return@launch
                        if (result is MqResult.Success && here) playback?.pause()
                        sent.value = SentToTv(action.tv.deviceName, result is MqResult.Success)
                    }

                    MovieAction.ToggleDownload -> {
                        if (content.download == null) {
                            downloader.download(listOf(movie), DownloadOrigin.Movie(id))
                        } else {
                            downloader.remove(DownloadOrigin.Movie(id))
                        }
                    }

                    is MovieAction.ChooseAudio -> {
                        picker.choose(movie, action.index, resolved?.subtitleIndex, wholeSeason = false)
                    }

                    is MovieAction.ChooseSubtitle -> {
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
            fun create(id: MediaId): MovieViewModel
        }
    }
