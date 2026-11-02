package io.github.kentprodan.mediatheq.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel
    @AssistedInject
    constructor(
        @Assisted private val artistId: MediaId,
        libraryRepository: LibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
    ) : ViewModel() {
        private val content =
            combine(
                libraryRepository.artist(artistId),
                libraryRepository.artistAlbums(artistId),
                libraryRepository.appearsOn(artistId),
                libraryRepository.artistTracks(artistId),
            ) { artist, albums, appearsOn, tracks ->
                artist?.let { ArtistUiState.Content(it, albums, appearsOn, tracks, topTracks(tracks)) }
            }

        private val selection = MutableStateFlow(ListSelection<MediaId>())

        private val page =
            combine(
                content,
                preferencesRepository.preferences
                    .map { it.albumColumns to it.ratingMode }
                    .distinctUntilChanged(),
                // Only the track id: position ticks and play/pause must not rebuild the page.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { content, (columns, ratingMode), currentTrackId ->
                content?.copy(albumColumns = columns, currentTrackId = currentTrackId, ratingMode = ratingMode)
                    ?: ArtistUiState.NotFound
            }

        val uiState: StateFlow<ArtistUiState> =
            combine(page, selection) { page, selection ->
                // Tracks can leave the page while selected.
                (page as? ArtistUiState.Content)?.let {
                    it.copy(
                        selection =
                            selection.retain(
                                it.topTracks.map { t ->
                                    t.id
                                },
                            ),
                    )
                }
                    ?: page
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState.Loading)

        fun onAction(action: ArtistAction) {
            val state = uiState.value as? ArtistUiState.Content ?: return
            when (action) {
                ArtistAction.Play -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks)
                }

                // The player picks the random start and keeps the order for unshuffling (PlaybackConnection).
                ArtistAction.Shuffle -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks, shuffle = true)
                }

                is ArtistAction.PlayTopTrack -> {
                    val index = state.topTracks.indexOfFirst { it.id == action.trackId }
                    viewModelScope.launch { trackTaps.tap(state.topTracks, index) }
                }

                is ArtistAction.SwipeTrack -> {
                    viewModelScope.launch {
                        commands.swipe(action.track, action.right)
                    }
                }

                is ArtistAction.RateTrack -> {
                    viewModelScope.launch {
                        commands.rate(action.track, action.halfStars)
                    }
                }

                is ArtistAction.ToggleSelection -> {
                    selection.update { it.toggle(action.id) }
                }

                ArtistAction.SelectAll -> {
                    selection.value = ListSelection<MediaId>().selectAll((state.topTracks).map { it.id })
                }

                ArtistAction.ClearSelection -> {
                    selection.value = ListSelection()
                }

                is ArtistAction.OnSelection -> {
                    val picked = selection.value.pick(state.topTracks) { it.id }
                    selection.value = ListSelection()
                    when (action.command) {
                        SelectionCommand.Play -> commands.run(TopButton.Play, picked)

                        SelectionCommand.PlayNext -> commands.run(TopButton.PlayNext, picked)

                        SelectionCommand.AddToQueue -> commands.run(TopButton.AddToQueue, picked)

                        SelectionCommand.Download -> commands.download(picked)

                        SelectionCommand.Favorite -> viewModelScope.launch { commands.toggleFavorite(picked) }

                        // The screen opens the playlist picker with the tracks it shows.
                        SelectionCommand.AddToPlaylist -> Unit
                    }
                }

                is ArtistAction.SetFavorite -> {
                    viewModelScope.launch { userData.setFavorite(artistId, action.isFavorite) }
                }

                is ArtistAction.Rate -> {
                    viewModelScope.launch { userData.setRating(artistId, action.halfStars) }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(artistId: MediaId): ArtistViewModel
        }
    }

/** The most played tracks; ties keep the discography order (the sort is stable). */
internal fun topTracks(tracks: List<Track>): List<Track> =
    tracks.filter { it.userData.playCount > 0 }.sortedByDescending { it.userData.playCount }.take(TOP_TRACK_COUNT)

private const val TOP_TRACK_COUNT = 5
