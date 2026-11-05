package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.CoverTap
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LyricsRepository
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.WaveformRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.ui.qualityLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
        private val lyricsRepository: LyricsRepository,
        private val libraryPreferences: LibraryPreferencesRepository,
        waveforms: WaveformRepository,
        private val downloads: DownloadRepository,
    ) : ViewModel() {
        private val lyricsShown = MutableStateFlow(false)

        // Computed on the device (#124): appears while the track plays, once its file is whole.
        private val playingWaveform: Flow<Pair<MediaId, List<Float>?>?> =
            combine(
                playback.state.map { it.item?.id }.distinctUntilChanged(),
                // A plain bar as the chosen style (#126) never shows a waveform: none is read.
                libraryPreferences.preferences.map { it.nowPlaying.progress.usesWaveform }.distinctUntilChanged(),
                ::Pair,
            ).flatMapLatest { (id, wanted) ->
                if (id == null || !wanted) flowOf(null) else waveforms.levels(id).map { id to it }
            }

        // Favorite, rating, artists, album and format come from Room, so a change made here or in another client
        // shows at once.
        private val playingTrack: Flow<Pair<MediaId, Track>?> =
            playback.state
                .map { it.item?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id ==
                        null
                    ) {
                        flowOf(null)
                    } else {
                        library.track(id).map { track -> track?.let { id to it } }
                    }
                }

        val uiState: StateFlow<NowPlayingUiState> =
            combine(
                playback.state,
                playingTrack,
                libraryPreferences.preferences
                    .map { Triple(it.ratingMode, it.qualityDetail, it.nowPlaying) }
                    .distinctUntilChanged(),
                playingWaveform,
                lyricsShown,
            ) { state, data, (ratingMode, qualityDetail, settings), waveform, lyrics ->
                // Until the new track's rows arrive, the previous track's data and waveform must not show.
                val track = data?.takeIf { it.first == state.item?.id }?.second
                val ui = state.toUiState(track?.userData, ratingMode)
                val levels = waveform?.takeIf { it.first == state.item?.id }?.second
                if (ui is NowPlayingUiState.Playing) {
                    ui.copy(
                        waveform = levels,
                        artists = track?.artists.orEmpty(),
                        albumId = track?.albumId,
                        albumTitle = track?.albumTitle,
                        // The output extras arrive apart from the item: only this item's count.
                        quality =
                            state.output
                                .takeIf { it.playedItemId == state.item?.id?.toString() }
                                ?.let { qualityLabel(track?.audioFormat, it.streamKnown, it.convertedKbps) },
                        qualityDetail = qualityDetail,
                        lyricsShown = lyrics,
                        settings = settings,
                    )
                } else {
                    ui
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                playback.state.value.toUiState(null, RatingMode.WholeStars),
            )

        /** The playing position, sampled by the connection; collect it only where it is drawn. */
        val position: StateFlow<Duration> = playback.position

        private val lyricsRetries = MutableStateFlow(0)

        /** The playing track's lyrics, asked of the server when the track changes or the player reopens; collect it only while the player is open. */
        val lyrics: StateFlow<LyricsUiState> =
            combine(playback.state.map { it.item?.id }.distinctUntilChanged(), lyricsRetries) { id, _ -> id }
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf<LyricsUiState>(LyricsUiState.None)
                    } else {
                        flow {
                            emit(LyricsUiState.Loading)
                            emit(
                                when (val result = lyricsRepository.lyrics(id)) {
                                    is MqResult.Success -> {
                                        result.value?.let { LyricsUiState.Content(it) }
                                            ?: LyricsUiState.None
                                    }

                                    is MqResult.Failure -> {
                                        LyricsUiState.Error(result.error)
                                    }
                                },
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Loading)

        /** The queue in play order; collect it only while the queue screen shows. */
        val queue: StateFlow<List<QueueItem>> = playback.queue

        private val expandPlayer =
            libraryPreferences.preferences
                .map { it.navigation.expandPlayer }
                .distinctUntilChanged()

        /**
         * Now Playing should open by itself (#122): playback was just started from a list, the setting is on and the
         * track is there to show. The player answers with [NowPlayingAction.AutoExpanded].
         */
        val autoExpand: StateFlow<Boolean> =
            combine(
                playback.expandRequested,
                expandPlayer,
                playback.state.map { it.item != null }.distinctUntilChanged(),
            ) { requested, on, hasItem -> requested && on && hasItem }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        init {
            // With the setting off a request is dropped at once, so turning it on later never opens a stale one.
            viewModelScope.launch {
                combine(playback.expandRequested, expandPlayer, ::Pair).collect { (requested, on) ->
                    if (requested && !on) playback.acknowledgeExpand()
                }
            }
        }

        fun onAction(action: NowPlayingAction) {
            when (action) {
                NowPlayingAction.TogglePlayPause -> {
                    playback.togglePlayPause()
                }

                NowPlayingAction.AutoExpanded -> {
                    playback.acknowledgeExpand()
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

                is NowPlayingAction.SeekToTime -> {
                    val state = playback.state.value
                    // A live transcode cannot be seeked (R12).
                    if (state.isSeekable && state.duration.isPositive()) {
                        playback.seekTo(action.position.coerceIn(Duration.ZERO, state.duration))
                    }
                }

                is NowPlayingAction.SetShuffle -> {
                    playback.setShuffle(action.enabled)
                }

                is NowPlayingAction.SetRepeatMode -> {
                    playback.setRepeatMode(action.mode)
                }

                NowPlayingAction.RetryLyrics -> {
                    lyricsRetries.value++
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
                    viewModelScope.launch { userData.setRating(id, action.halfStars) }
                }

                NowPlayingAction.ToggleLyrics -> {
                    lyricsShown.value = !lyricsShown.value
                }

                NowPlayingAction.CoverTapped -> {
                    when ((uiState.value as? NowPlayingUiState.Playing)?.settings?.coverTap) {
                        CoverTap.PlayPause -> playback.togglePlayPause()
                        CoverTap.ShowLyrics -> lyricsShown.value = true
                        CoverTap.None, null -> Unit
                    }
                }

                NowPlayingAction.ToggleQualityDetail -> {
                    val detail = (uiState.value as? NowPlayingUiState.Playing)?.qualityDetail ?: return
                    viewModelScope.launch { libraryPreferences.setQualityDetail(!detail) }
                }

                NowPlayingAction.Download -> {
                    val id =
                        playback.state.value.item
                            ?.id ?: return
                    viewModelScope.launch { library.track(id).first()?.let { downloads.download(listOf(it)) } }
                }

                is NowPlayingAction.MoveQueueItem -> {
                    playback.moveQueueItem(action.entryId, action.to)
                }
            }
        }
    }

private fun PlayerUiModel.toUiState(
    userData: UserData?,
    ratingMode: RatingMode,
): NowPlayingUiState {
    val item = item ?: return NowPlayingUiState.Hidden
    return NowPlayingUiState.Playing(
        title = item.title,
        artist = item.artist,
        trackId = item.id,
        artwork = item.artwork,
        isPlaying = isPlaying,
        duration = duration,
        isSeekable = isSeekable,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        currentEntryId = currentEntryId,
        shuffle = shuffle,
        repeatMode = repeatMode,
        isFavorite = userData?.isFavorite == true,
        halfStars = userData?.halfStars,
        ratingMode = ratingMode,
        bitPerfectSampleRateHz = output.bitPerfectSampleRateHz,
        atmosConverted = output.atmosConverted,
    )
}
