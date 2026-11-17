package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DisplayMatch
import io.github.kentprodan.mediatheq.core.data.SegmentAction
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.data.segmentAction
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.PlayerExtras
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The TV video player (#429): plays the key's video through the session (10 s before a resume point), follows the
 * next episode when it starts, and counts down to it in the last [NEXT_COUNTDOWN]. The picture comes from
 * [PlaybackConnection.videoPlayer]; every command goes through the connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TvPlayerViewModel.Factory::class)
class TvPlayerViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: VideoPlayerKey,
        private val videos: VideoRepository,
        private val playback: PlaybackConnection,
        private val picker: TrackPicker,
        private val preferences: VideoPreferencesRepository = VideoPreferencesRepository.Defaults,
    ) : ViewModel() {
        /** How subtitles look (TV Settings, #429). */
        val subtitleStyle: StateFlow<SubtitleStyle> =
            preferences.preferences
                .map { it.subtitleStyle }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle())

        /** How the display follows the video (#384) and the stream's frame rate and height once known. */
        val displayMatch: StateFlow<DisplayMatch> =
            preferences.preferences
                .map { it.displayMatch }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplayMatch.Off)
        val videoFormat: StateFlow<Pair<Float, Int>?> =
            playback.state
                .map { player -> player.output.video?.let { v -> v.frameRate?.let { it to v.height } } }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** The streaming quality the TV asks for (its network is unmetered); the Quality menu changes it. */
        val quality: StateFlow<VideoQuality> =
            preferences.preferences
                .map { it.unmeteredQuality }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoQuality.Original)

        private val videoId = MutableStateFlow(key.id)
        private val video = videoId.flatMapLatest { videos.video(it) }
        private val tracks = video.flatMapLatest { it?.let(picker::tracks) ?: flowOf(null) }

        // Set once this screen's video played: a video ending after that ends the screen.
        private val started = MutableStateFlow(false)

        val position: StateFlow<Duration> = playback.position
        val videoPlayer = playback.videoPlayer

        val uiState: StateFlow<TvPlayerUiState> =
            combine(video, playback.state, tracks, started) { video, player, tracks, seen ->
                val item = player.item?.takeIf { it.isVideo && it.id == video?.id }
                when {
                    video == null -> {
                        TvPlayerUiState.Error(MqError.NotFound)
                    }

                    item == null -> {
                        if (seen && player.item?.isVideo != true) TvPlayerUiState.Ended else TvPlayerUiState.Loading
                    }

                    // Video mode or no music queue to hand back: the ended video stays the item.
                    seen && player.isEnded -> {
                        TvPlayerUiState.Ended
                    }

                    player.isFailed -> {
                        TvPlayerUiState.Error(MqError.Client(0, "playback"))
                    }

                    else -> {
                        TvPlayerUiState.Playing(
                            video = video,
                            isPlaying = player.isPlaying,
                            isBuffering = player.isBuffering,
                            duration = player.duration.takeIf { it > Duration.ZERO } ?: video.duration,
                            hasNext = player.hasNext,
                            tracks = tracks,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvPlayerUiState.Loading)

        /** The playing video's chapters and seek previews (#430); none while loading, offline or without. */
        val extras: StateFlow<PlayerExtras> =
            videoId
                .mapLatest { videos.playerExtras(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerExtras())

        /** The segment whose Skip button shows now (#430, the phone's rule): never automatic unless set so. */
        val skipButton: StateFlow<MediaSegment?> get() = button
        private val button = MutableStateFlow<MediaSegment?>(null)
        private val skipped = mutableSetOf<MediaSegment>()
        private val buttonShownAt = mutableMapOf<MediaSegment, Duration>()

        /** Seconds until the next episode starts, in the last [NEXT_COUNTDOWN] with one queued; `null` otherwise. */
        val nextIn: StateFlow<Int?> =
            combine(playback.state, playback.position) { player, at ->
                val left = player.duration - at
                val ending = player.item?.isVideo == true && player.hasNext && player.duration > Duration.ZERO
                left.takeIf { ending && it <= NEXT_COUNTDOWN && it >= Duration.ZERO }?.let {
                    ceil(it.inWholeMilliseconds / 1000.0).toInt()
                }
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        init {
            viewModelScope.launch {
                videoId.collectLatest { id ->
                    skipped.clear()
                    buttonShownAt.clear()
                    button.value = null
                    val segments = videos.segments(id)
                    if (segments.isEmpty()) return@collectLatest
                    combine(playback.position, playback.state, preferences.preferences) { at, player, settings ->
                        if (player.item?.id != id) {
                            SegmentAction.None
                        } else {
                            segmentAction(
                                at,
                                segments,
                                settings,
                                skipped,
                                buttonShownAt,
                                controlsShown = !player.isPlaying,
                            )
                        }
                    }.collect { action ->
                        when (action) {
                            SegmentAction.None -> {
                                button.value = null
                            }

                            is SegmentAction.Button -> {
                                buttonShownAt.putIfAbsent(action.segment, playback.position.value)
                                button.value = action.segment
                            }

                            is SegmentAction.Skip -> {
                                skip(action.segment)
                            }
                        }
                    }
                }
            }
            if (!key.attach) {
                viewModelScope.launch {
                    videos.video(key.id).first()?.let {
                        val saved = key.startPositionMs.milliseconds
                        playback.playVideo(it, if (saved > Duration.ZERO) resumeStart(saved) else Duration.ZERO)
                    }
                }
            }
            viewModelScope.launch {
                playback.state.collect { player ->
                    val id = player.item?.takeIf { it.isVideo }?.id ?: return@collect
                    if (id == key.id) started.value = true
                    // The next episode took over: the screen follows it.
                    if (started.value) videoId.value = id
                }
            }
        }

        fun onAction(action: TvPlayerAction) {
            val playing = uiState.value as? TvPlayerUiState.Playing
            when (action) {
                TvPlayerAction.TogglePlayPause -> {
                    playback.togglePlayPause()
                }

                is TvPlayerAction.Seek -> {
                    val duration = playing?.duration ?: return
                    playback.seekTo(videoSeekTarget(position.value, duration, action.forward, action.repeat))
                }

                is TvPlayerAction.ChooseAudio -> {
                    val video = playing?.video ?: return
                    choose(video) { picker.choose(video, action.index, it?.subtitleIndex, wholeSeason = false) }
                }

                is TvPlayerAction.ChooseSubtitle -> {
                    val video = playing?.video ?: return
                    choose(video) {
                        picker.choose(video, it?.audioIndex, action.index, wholeSeason = false, action.index == null)
                    }
                }

                TvPlayerAction.CancelNext -> {
                    playback.cancelNext()
                }

                TvPlayerAction.SkipSegment -> {
                    button.value?.let(::skip)
                }

                is TvPlayerAction.SeekTo -> {
                    playback.seekTo(action.position)
                }

                TvPlayerAction.PlayNextNow -> {
                    playback.next()
                }

                // Kept for the next videos too (the setting of the TV); the stream opens again at once with it.
                is TvPlayerAction.SetQuality -> {
                    viewModelScope.launch {
                        preferences.update { it.copy(unmeteredQuality = action.quality) }
                        if (playback.state.value.item
                                ?.isVideo == true
                        ) {
                            playback.reloadVideo()
                        }
                    }
                }

                // Leaving stops the picture's sound too: on a TV nothing plays a video in the background.
                TvPlayerAction.Leave -> {
                    playback.pause()
                }
            }
        }

        /** Skips [segment] once, to its end (#430). */
        private fun skip(segment: MediaSegment) {
            skipped += segment
            button.value = null
            playback.seekTo(segment.end)
        }

        /** Saves the choice, then opens the stream again with it (the server converts with the chosen tracks). */
        private fun choose(
            video: Video,
            save: suspend (io.github.kentprodan.mediatheq.core.model.ResolvedTracks?) -> Unit,
        ) {
            viewModelScope.launch {
                save(tracks.first()?.resolved)
                if (playback.state.value.item
                        ?.id == video.id
                ) {
                    playback.reloadVideo()
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(key: VideoPlayerKey): TvPlayerViewModel
        }

        companion object {
            /** The next episode's countdown (owner: "Next episode in 10 s"). */
            val NEXT_COUNTDOWN = 10.seconds
        }
    }

sealed interface TvPlayerUiState {
    data object Loading : TvPlayerUiState

    data class Playing(
        val video: Video,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val duration: Duration,
        /** The next episode is queued after this one. */
        val hasNext: Boolean,
        val tracks: VideoTracks? = null,
    ) : TvPlayerUiState

    /** The video ended (and nothing followed): the screen closes. */
    data object Ended : TvPlayerUiState

    data class Error(
        val error: MqError,
    ) : TvPlayerUiState
}

sealed interface TvPlayerAction {
    data object TogglePlayPause : TvPlayerAction

    /** Left or Right; [repeat] counts the held key's repeats ([videoSeekTarget]). */
    data class Seek(
        val forward: Boolean,
        val repeat: Int = 0,
    ) : TvPlayerAction

    data class ChooseAudio(
        val index: Int,
    ) : TvPlayerAction

    /** `null` = Off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : TvPlayerAction

    data object CancelNext : TvPlayerAction

    /** The Skip intro / Skip credits button (#430). */
    data object SkipSegment : TvPlayerAction

    /** A chapter chosen from the list (#430). */
    data class SeekTo(
        val position: Duration,
    ) : TvPlayerAction

    data class SetQuality(
        val quality: VideoQuality,
    ) : TvPlayerAction

    data object PlayNextNow : TvPlayerAction

    data object Leave : TvPlayerAction
}
