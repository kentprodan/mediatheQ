package io.github.kentprodan.mediatheq.feature.tv.video

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.HeldKey
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DisplayMatch
import io.github.kentprodan.mediatheq.core.data.SegmentAction
import io.github.kentprodan.mediatheq.core.data.SubtitleDelayRepository
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.data.resumeStart
import io.github.kentprodan.mediatheq.core.data.segmentAction
import io.github.kentprodan.mediatheq.core.data.subtitleDelayStep
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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
        private val delays: SubtitleDelayRepository,
        private val preferences: VideoPreferencesRepository = VideoPreferencesRepository.Defaults,
        private val clock: Clock = Clock.System,
    ) : ViewModel() {
        // "Still watching?" (#388): episodes that started by themselves and when a key was last pressed.
        private var autoStarted = 0
        private var lastKey = clock.now()
        private val asking = MutableStateFlow(false)

        /** How much later (negative: earlier) the playing video's subtitles show (#456). */
        val subtitleDelay: StateFlow<Duration> by lazy {
            uiState
                .map { (it as? TvPlayerUiState.Playing)?.video?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> id?.let(delays::delay) ?: flowOf(Duration.ZERO) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Duration.ZERO)
        }

        /** The second subtitle track's language and cues (#635). */
        val secondarySubtitleLanguage = playback.secondarySubtitleLanguage
        val secondarySubtitleCues = playback.secondarySubtitleCues

        /** The player asks whether anyone still watches; playback is paused until the answer. */
        val stillWatching: StateFlow<Boolean> = asking

        /** How subtitles look (TV Settings, #429). */
        val subtitleStyle: StateFlow<SubtitleStyle> =
            preferences.preferences
                .map { it.subtitleStyle }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle())

        /** How the display follows the video (#384). */
        val displayMatch: StateFlow<DisplayMatch> =
            preferences.preferences
                .map { it.displayMatch }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplayMatch.Off)

        /** The streaming quality the TV asks for (its network is unmetered); the Quality menu changes it. */
        val quality: StateFlow<VideoQuality> =
            preferences.preferences
                .map { it.unmeteredQuality }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoQuality.Original)

        private val videoId = MutableStateFlow(key.id)
        private val video = videoId.flatMapLatest { videos.video(it) }

        /**
         * The frame rate and height the display follows: the library's (known before playback, so the mode switches
         * before any sound, #631), else the stream's once it plays.
         */
        val videoFormat: StateFlow<Pair<Float, Int>?> =
            combine(video, playback.state) { video, player ->
                val stream = player.output.video?.takeIf { player.item?.id == video?.id }
                val height = video?.format?.height ?: stream?.height ?: 0
                // The frames' own timing wins once measured (#633): 3:2 pulldown stated as 23.976 wants 59.94.
                stream?.cadenceRate?.let { it to height }
                    ?: video?.format?.frameRate?.let { it to height }
                    ?: stream?.frameRate?.let { it to stream.height }
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        // The display mode switch (#631): the first start waits for it to settle; a switch while playing pauses and
        // then seeks in place, which opens a new audio track on the renegotiated HDMI route.
        private val displaySettled = MutableStateFlow(false)
        private var resumeAfterSwitch = false
        private val tracks = video.flatMapLatest { it?.let(picker::tracks) ?: flowOf(null) }

        // Set once this screen's video played: a video ending after that ends the screen.
        private val started = MutableStateFlow(false)

        val position: StateFlow<Duration> = playback.position

        // A held Left or Right (#353): told by timing, per video; repeats step on from the last target.
        private val held = HeldKey()
        private var heldTarget: Duration? = null
        val videoPlayer = playback.videoPlayer

        /** The view drawing styled ASS subtitles over the picture (#635). */
        fun styledSubtitleOverlay(context: Context) = playback.styledSubtitleOverlay(context)

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
                        val match = preferences.preferences.first().displayMatch
                        val switches = it.format?.frameRate != null && match != DisplayMatch.Off
                        if (switches) withTimeoutOrNull(START_HOLD_MAX) { displaySettled.first { ready -> ready } }
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
                    if (started.value && id != videoId.value) {
                        videoId.value = id
                        onEpisodeStartedByItself()
                    }
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
                    val repeat = action.atMs?.let { held.press(action.forward, it, playing.video.id) ?: return } ?: 0
                    if (repeat == 0) heldTarget = null
                    // A held key steps on from its last target: the position lags a round trip behind.
                    val target = videoSeekTarget(heldTarget ?: position.value, duration, action.forward, repeat)
                    heldTarget = target
                    playback.seekTo(target)
                }

                is TvPlayerAction.ChooseAudio -> {
                    val video = playing?.video ?: return
                    choose(video) { picker.choose(video, action.index, it?.subtitleIndex, wholeSeason = false) }
                }

                is TvPlayerAction.ChooseSecondSubtitles -> {
                    playback.chooseSecondarySubtitles(action.language)
                }

                is TvPlayerAction.ShiftSubtitles -> {
                    val video = playing?.video ?: return
                    val delay =
                        if (action.later == null) {
                            Duration.ZERO
                        } else {
                            subtitleDelayStep(subtitleDelay.value, action.later)
                        }
                    viewModelScope.launch { delays.save(video.id, delay) }
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

                // Any key on the remote: someone is watching.
                TvPlayerAction.KeyPressed -> {
                    lastKey = clock.now()
                    autoStarted = 0
                }

                TvPlayerAction.KeepWatching -> {
                    asking.value = false
                    lastKey = clock.now()
                    autoStarted = 0
                    if (!playback.state.value.isPlaying) playback.togglePlayPause()
                }

                TvPlayerAction.SkipSegment -> {
                    button.value?.let(::skip)
                }

                TvPlayerAction.DisplaySwitching -> {
                    if (started.value && playback.state.value.isPlaying) {
                        playback.pause()
                        resumeAfterSwitch = true
                    }
                }

                TvPlayerAction.DisplaySettled -> {
                    displaySettled.value = true
                    if (resumeAfterSwitch) {
                        resumeAfterSwitch = false
                        playback.seekTo(position.value)
                        if (!playback.state.value.isPlaying) playback.togglePlayPause()
                    }
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

        /** After [STILL_WATCHING_EPISODES] episodes or [STILL_WATCHING_TIME] without a key, the player asks first. */
        private fun onEpisodeStartedByItself() {
            autoStarted++
            if (stillWatchingDue(autoStarted, clock.now() - lastKey)) {
                playback.pause()
                asking.value = true
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

            /** The first start never waits longer for the display than this (the settle itself stops at 8 s). */
            val START_HOLD_MAX = 10.seconds
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

    /**
     * Left or Right, a key-down at [atMs] (uptime); held keys are told by timing ([HeldKey], #353). Without a time
     * (the bar's -10 s / +30 s buttons) it is a press of its own.
     */
    data class Seek(
        val forward: Boolean,
        val atMs: Long? = null,
    ) : TvPlayerAction

    data class ChooseAudio(
        val index: Int,
    ) : TvPlayerAction

    /** `null` = Off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : TvPlayerAction

    /** A second subtitle track in [language] at the top (#635); `null` = none. */
    data class ChooseSecondSubtitles(
        val language: String?,
    ) : TvPlayerAction

    /** Subtitles 0.1 s [later] or earlier (#456); `null` back in step with the picture. */
    data class ShiftSubtitles(
        val later: Boolean?,
    ) : TvPlayerAction

    data object CancelNext : TvPlayerAction

    /** A key of the remote was pressed (#388). */
    data object KeyPressed : TvPlayerAction

    /** "Still watching?" answered yes. */
    data object KeepWatching : TvPlayerAction

    /** The Skip intro / Skip credits button (#430). */
    data object SkipSegment : TvPlayerAction

    /** The display is about to change mode (#631): playback pauses until it settles. */
    data object DisplaySwitching : TvPlayerAction

    /** The display is in the video's mode and settled, or needed no switch (#631). */
    data object DisplaySettled : TvPlayerAction

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

/** Owner, #388: ask after 3 episodes in a row or 3 hours without a key, whichever comes first. */
internal fun stillWatchingDue(
    episodesStartedByThemselves: Int,
    sinceLastKey: Duration,
): Boolean = episodesStartedByThemselves >= STILL_WATCHING_EPISODES || sinceLastKey >= STILL_WATCHING_TIME

private const val STILL_WATCHING_EPISODES = 3
private val STILL_WATCHING_TIME = 3.hours
