package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class NowPlayingViewModel
    @Inject
    constructor(
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<NowPlayingUiState> =
            playback.state
                .map { it.toUiState() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playback.state.value.toUiState())

        /** The playing position, sampled by the connection; collect it only where it is drawn. */
        val position: StateFlow<Duration> = playback.position

        /** The queue in play order; collect it only while the queue screen shows. */
        val queue: StateFlow<List<QueueItem>> = playback.queue

        fun onAction(action: NowPlayingAction) {
            when (action) {
                NowPlayingAction.TogglePlayPause -> {
                    playback.togglePlayPause()
                }

                NowPlayingAction.Next -> {
                    playback.next()
                }

                NowPlayingAction.Previous -> {
                    playback.previous()
                }

                is NowPlayingAction.SeekTo -> {
                    val duration = playback.state.value.duration
                    if (duration.isPositive()) playback.seekTo(duration * action.fraction.coerceIn(0f, 1f).toDouble())
                }

                is NowPlayingAction.SkipToQueueItem -> {
                    playback.skipToQueueItem(action.entryId)
                }

                is NowPlayingAction.RemoveQueueItem -> {
                    playback.removeQueueItem(action.entryId)
                }

                is NowPlayingAction.MoveQueueItem -> {
                    playback.moveQueueItem(action.entryId, action.to)
                }
            }
        }
    }

private fun PlayerUiModel.toUiState(): NowPlayingUiState {
    val item = item ?: return NowPlayingUiState.Hidden
    return NowPlayingUiState.Playing(
        title = item.title,
        artist = item.artist,
        artwork = item.artwork,
        isPlaying = isPlaying,
        duration = duration,
        isSeekable = isSeekable,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        currentEntryId = currentEntryId,
        shuffle = shuffle,
    )
}
