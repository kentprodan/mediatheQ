package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel
    @Inject
    constructor(
        private val playback: PlaybackConnection,
        private val library: LibraryRepository,
        private val userData: UserDataRepository,
    ) : ViewModel() {
        // Favorite and rating come from Room, so a change made here or in another client shows at once.
        private val playingUserData: Flow<Pair<MediaId, UserData>?> =
            playback.state
                .map { it.item?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id ==
                        null
                    ) {
                        flowOf(null)
                    } else {
                        library.track(id).map { track -> track?.let { id to it.userData } }
                    }
                }

        val uiState: StateFlow<NowPlayingUiState> =
            combine(playback.state, playingUserData) { state, data ->
                // Until the new track's row arrives, the previous track's user data must not show.
                state.toUiState(data?.takeIf { it.first == state.item?.id }?.second)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playback.state.value.toUiState(null))

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

                is NowPlayingAction.SetFavorite -> {
                    val id =
                        playback.state.value.item
                            ?.id ?: return
                    viewModelScope.launch { userData.setFavorite(id, action.isFavorite) }
                }

                is NowPlayingAction.Rate -> {
                    val id =
                        playback.state.value.item
                            ?.id ?: return
                    viewModelScope.launch { userData.setRating(id, action.stars) }
                }

                is NowPlayingAction.MoveQueueItem -> {
                    playback.moveQueueItem(action.entryId, action.to)
                }
            }
        }
    }

private fun PlayerUiModel.toUiState(userData: UserData?): NowPlayingUiState {
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
        isFavorite = userData?.isFavorite == true,
        stars = userData?.stars,
    )
}
