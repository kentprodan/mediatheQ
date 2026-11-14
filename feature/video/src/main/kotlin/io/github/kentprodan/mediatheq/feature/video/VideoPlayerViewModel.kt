package io.github.kentprodan.mediatheq.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The full-screen video player (ADR 0047, 0049): starts the video in the session's player (unless [key] attaches to it
 * as it plays) and drives the controls, which hide by themselves while it plays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
class VideoPlayerViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: VideoPlayerKey,
        videos: VideoRepository,
        private val playback: PlaybackConnection,
        private val picker: TrackPicker,
    ) : ViewModel() {
        private val controlsShown = MutableStateFlow(true)
        private var hideJob: Job? = null
        private val video = videos.video(key.id)

        // Set once the player had this video: its going away afterwards means it ended, not that it is loading.
        private var seen = false

        /** The surface's player (the connection's controller), `null` until connected. */
        val player = playback.videoPlayer

        /** Sampled by the connection while playing. */
        val position: StateFlow<Duration> = playback.position

        private val tracks = video.flatMapLatest { it?.let(picker::tracks) ?: flowOf(null) }

        val uiState: StateFlow<VideoPlayerUiState> =
            combine(video, playback.state, controlsShown, tracks) { video, player, shown, tracks ->
                val item = player.item?.takeIf { it.isVideo && it.id == key.id }
                if (item != null) seen = true
                when {
                    video == null -> {
                        VideoPlayerUiState.Error(MqError.NotFound)
                    }

                    item == null -> {
                        if (seen) VideoPlayerUiState.Ended else VideoPlayerUiState.Loading
                    }

                    player.isFailed -> {
                        VideoPlayerUiState.Error(MqError.Client(0, "playback"))
                    }

                    else -> {
                        VideoPlayerUiState.Playing(
                            title = video.title,
                            subtitle = video.subtitle(),
                            isPlaying = player.isPlaying,
                            isBuffering = player.isBuffering,
                            duration = player.duration.takeIf { it > Duration.ZERO } ?: video.duration,
                            isSeekable = player.isSeekable,
                            aspectRatio = player.videoAspectRatio,
                            // Paused or buffering, the controls stay: there is nothing to watch behind them.
                            controlsShown = shown || !player.isPlaying,
                            tracks = tracks,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoPlayerUiState.Loading)

        init {
            if (!key.attach) {
                viewModelScope.launch {
                    video.first()?.let { playback.playVideo(it, key.startPositionMs.milliseconds) }
                }
            }
            scheduleHide()
        }

        fun onAction(action: VideoPlayerAction) {
            when (action) {
                VideoPlayerAction.TogglePlayPause -> {
                    playback.togglePlayPause()
                    show()
                }

                is VideoPlayerAction.SeekTo -> {
                    playback.seekTo(action.position)
                    show()
                }

                VideoPlayerAction.ToggleControls -> {
                    if (controlsShown.value) {
                        hideJob?.cancel()
                        controlsShown.value = false
                    } else {
                        show()
                    }
                }

                VideoPlayerAction.Leave -> {
                    playback.pause()
                }

                is VideoPlayerAction.ChooseAudio -> {
                    choose { it.copy(audioIndex = action.index) }
                }

                is VideoPlayerAction.ChooseSubtitle -> {
                    choose(subtitleOff = action.index == null) { it.copy(subtitleIndex = action.index) }
                }

                is VideoPlayerAction.WholeSeason -> {
                    choose(wholeSeason = action.on) { it }
                }
            }
        }

        /** Saves the tracks [change] makes (on the episode, or its season), then plays on with them where it is. */
        private fun choose(
            wholeSeason: Boolean? = null,
            subtitleOff: Boolean = false,
            change: (ResolvedTracks) -> ResolvedTracks,
        ) {
            val playing = uiState.value as? VideoPlayerUiState.Playing ?: return
            val current = playing.tracks ?: return
            viewModelScope.launch {
                val video = video.first() ?: return@launch
                val tracks = change(current.resolved)
                picker.choose(
                    video,
                    tracks.audioIndex,
                    tracks.subtitleIndex,
                    wholeSeason ?: (current.wholeSeason == true),
                    subtitleOff,
                )
                playback.reloadVideo()
            }
        }

        private fun show() {
            controlsShown.value = true
            scheduleHide()
        }

        private fun scheduleHide() {
            hideJob?.cancel()
            hideJob =
                viewModelScope.launch {
                    delay(CONTROLS_TIMEOUT)
                    controlsShown.value = false
                }
        }

        @AssistedFactory
        interface Factory {
            fun create(key: VideoPlayerKey): VideoPlayerViewModel
        }

        companion object {
            /** The controls hide this long after the last touch while the video plays. */
            val CONTROLS_TIMEOUT = 3.seconds
        }
    }

/** "Series · S2 E4" for an episode, the year for a movie. */
internal fun Video.subtitle(): String? =
    when (kind) {
        VideoKind.Movie -> {
            year?.toString()
        }

        VideoKind.Episode -> {
            val number = listOfNotNull(seasonNumber?.let { "S$it" }, episodeNumber?.let { "E$it" }).joinToString(" ")
            listOfNotNull(seriesName, number.ifEmpty { null }).joinToString(" · ").ifEmpty { null }
        }
    }
