package io.github.kentprodan.mediatheq.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel
    @AssistedInject
    constructor(
        @Assisted private val artistId: MediaId,
        libraryRepository: LibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
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

        val uiState: StateFlow<ArtistUiState> =
            combine(
                content,
                preferencesRepository.preferences.map { it.albumColumns }.distinctUntilChanged(),
                // Only the track id: position ticks and play/pause must not rebuild the page.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { content, columns, currentTrackId ->
                content?.copy(albumColumns = columns, currentTrackId = currentTrackId) ?: ArtistUiState.NotFound
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
                    if (index >= 0) playback.play(state.topTracks, startIndex = index)
                }

                is ArtistAction.SetFavorite -> {
                    viewModelScope.launch { userData.setFavorite(artistId, action.isFavorite) }
                }

                is ArtistAction.Rate -> {
                    viewModelScope.launch { userData.setRating(artistId, action.stars) }
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
