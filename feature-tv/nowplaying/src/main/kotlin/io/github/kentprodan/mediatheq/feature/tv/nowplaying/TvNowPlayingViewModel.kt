package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.common.qualityLabel
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LyricsRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The full-screen player on TV (#240). */
sealed interface TvNowPlayingUiState {
    /** Nothing is queued. */
    data object Idle : TvNowPlayingUiState

    data class Playing(
        val title: String,
        val artist: String,
        val album: String? = null,
        val artwork: Artwork? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val duration: Duration = Duration.ZERO,
        val isSeekable: Boolean = false,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false,
        val shuffle: Boolean = false,
        val repeatMode: RepeatMode = RepeatMode.Off,
        val isFavorite: Boolean = false,
        /**
         * What actually plays (the file or the server's conversion), once the stream is known (#125). Its "Dolby Atmos"
         * kind shows only while Atmos really leaves the device (#242, ADR 0039).
         */
        val quality: QualityLabel? = null,
        /** An Atmos file this output plays as the server's stereo conversion. */
        val atmosConverted: Boolean = false,
        /** An Atmos file played as it is but decoded here: no Atmos reaches this output. */
        val atmosDecoded: Boolean = false,
        /** The queue in play order, the playing entry marked by [currentEntryId]. */
        val queue: List<QueueItem> = emptyList(),
        val currentEntryId: String? = null,
    ) : TvNowPlayingUiState
}

/** The playing track's lyrics on TV (#245); synced or plain, see [Lyrics.isSynced]. */
sealed interface TvLyricsUiState {
    data object Loading : TvLyricsUiState

    data class Content(
        val lyrics: Lyrics,
    ) : TvLyricsUiState

    /** The track has no lyrics. */
    data object None : TvLyricsUiState

    /** The server could not be asked; the next track asks again. */
    data object Error : TvLyricsUiState
}

sealed interface TvNowPlayingAction {
    data object TogglePlayPause : TvNowPlayingAction

    data object Next : TvNowPlayingAction

    data object Previous : TvNowPlayingAction

    data object ToggleShuffle : TvNowPlayingAction

    /** Off -> all -> one -> off. */
    data object CycleRepeat : TvNowPlayingAction

    data object ToggleFavorite : TvNowPlayingAction

    /** Left or Right on the progress row; [repeat] counts the held key's repeats ([seekTarget]). */
    data class Seek(
        val forward: Boolean,
        val repeat: Int = 0,
    ) : TvNowPlayingAction

    data class SkipTo(
        val entryId: String,
    ) : TvNowPlayingAction

    data class Remove(
        val entryId: String,
    ) : TvNowPlayingAction
}

/**
 * Where a D-pad seek lands (#240): 10 s a press, 30 s a step while the key is held past its first repeats, kept
 * inside the track.
 */
fun seekTarget(
    position: Duration,
    duration: Duration,
    forward: Boolean,
    repeat: Int,
): Duration {
    val step = if (repeat >= FAST_AFTER_REPEATS) FAST_STEP else STEP
    val target = if (forward) position + step else position - step
    return target.coerceIn(Duration.ZERO, duration)
}

private val STEP = 10.seconds
private val FAST_STEP = 30.seconds
private const val FAST_AFTER_REPEATS = 3
private const val SEEK_EVERY_REPEATS = 5

/** The full-screen player on TV (#240): `PlaybackConnection`'s state and commands, favorite and quality from Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvNowPlayingViewModel
    @javax.inject.Inject
    constructor(
        private val playback: PlaybackConnection,
        library: LibraryRepository,
        private val userData: UserDataRepository,
        lyricsRepository: LyricsRepository,
    ) : ViewModel() {
        // Favorite, album and format come from Room, so a change made here or on the phone shows at once.
        private val track =
            playback.state
                .map { it.item?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> id?.let { library.track(it) } ?: flowOf(null) }

        val uiState: StateFlow<TvNowPlayingUiState> =
            combine(playback.state, track, playback.queue) { state, track, queue -> state.toUiState(track, queue) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    playback.state.value.toUiState(null, emptyList()),
                )

        /** The playing track's lyrics (#245), asked of the server when the track changes. */
        val lyrics: StateFlow<TvLyricsUiState> =
            playback.state
                .map { it.item?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf<TvLyricsUiState>(TvLyricsUiState.None)
                    } else {
                        flow<TvLyricsUiState> {
                            emit(TvLyricsUiState.Loading)
                            emit(
                                when (val result = lyricsRepository.lyrics(id)) {
                                    is MqResult.Success -> {
                                        result.value?.let(TvLyricsUiState::Content)
                                            ?: TvLyricsUiState.None
                                    }

                                    is MqResult.Failure -> {
                                        TvLyricsUiState.Error
                                    }
                                },
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvLyricsUiState.Loading)

        /** The playing position, sampled by the connection; collect it only where it is drawn. */
        val position: StateFlow<Duration> = playback.position

        /** Where the held key's last seek went: repeats step on from it, the position lags a round trip behind. */
        private var heldTarget: Duration? = null

        private fun seek(
            action: TvNowPlayingAction.Seek,
            duration: Duration,
        ) {
            if (action.repeat == 0) heldTarget = null
            // A held key repeats about every 50 ms: one seek per few repeats, not a stream of re-buffers.
            if (action.repeat % SEEK_EVERY_REPEATS != 0) return
            val target = seekTarget(heldTarget ?: position.value, duration, action.forward, action.repeat)
            heldTarget = target
            playback.seekTo(target)
        }

        fun onAction(action: TvNowPlayingAction) {
            val state = uiState.value as? TvNowPlayingUiState.Playing ?: return
            when (action) {
                TvNowPlayingAction.TogglePlayPause -> {
                    playback.togglePlayPause()
                }

                TvNowPlayingAction.Next -> {
                    playback.next()
                }

                TvNowPlayingAction.Previous -> {
                    playback.previous()
                }

                TvNowPlayingAction.ToggleShuffle -> {
                    playback.setShuffle(!state.shuffle)
                }

                TvNowPlayingAction.CycleRepeat -> {
                    playback.setRepeatMode(
                        when (state.repeatMode) {
                            RepeatMode.Off -> RepeatMode.All
                            RepeatMode.All -> RepeatMode.One
                            RepeatMode.One -> RepeatMode.Off
                        },
                    )
                }

                TvNowPlayingAction.ToggleFavorite -> {
                    val id =
                        playback.state.value.item
                            ?.id ?: return
                    viewModelScope.launch { userData.setFavorite(id, !state.isFavorite) }
                }

                // A live transcode cannot be seeked (R12); an unknown duration would clamp every seek to the start.
                is TvNowPlayingAction.Seek -> {
                    if (state.isSeekable && state.duration.isPositive()) seek(action, state.duration)
                }

                is TvNowPlayingAction.SkipTo -> {
                    playback.skipToQueueItem(action.entryId)
                }

                // The playing entry stays: removing it would stop the music from the queue panel.
                is TvNowPlayingAction.Remove -> {
                    if (action.entryId != state.currentEntryId) playback.removeQueueItem(action.entryId)
                }
            }
        }
    }

private fun PlayerUiModel.toUiState(
    track: Track?,
    queue: List<QueueItem>,
): TvNowPlayingUiState {
    val item = item ?: return TvNowPlayingUiState.Idle
    // Until the new track's row arrives, the previous track's data must not show.
    val data = track?.takeIf { it.id == item.id }
    return TvNowPlayingUiState.Playing(
        title = item.title,
        artist = item.artist,
        album = data?.albumTitle,
        artwork = item.artwork,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        duration = duration,
        isSeekable = isSeekable,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        shuffle = shuffle,
        repeatMode = repeatMode,
        isFavorite = data?.userData?.isFavorite == true,
        // The output extras arrive apart from the item: only this item's count.
        quality =
            output
                .takeIf { it.playedItemId == item.id.toString() }
                ?.let { qualityLabel(data?.audioFormat, it.streamKnown, it.convertedKbps) }
                // The badge says Dolby Atmos only while the bitstream passes through, never from the metadata alone.
                ?.takeUnless { it.kind == QualityKind.DolbyAtmos && !output.atmosPassthrough },
        atmosConverted = output.atmosConverted,
        atmosDecoded = output.atmosDecoded,
        queue = queue,
        currentEntryId = currentEntryId,
    )
}
