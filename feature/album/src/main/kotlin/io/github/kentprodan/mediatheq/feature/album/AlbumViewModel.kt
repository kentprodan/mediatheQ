package io.github.kentprodan.mediatheq.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
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

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel
    @AssistedInject
    constructor(
        @Assisted private val albumId: MediaId,
        libraryRepository: LibraryRepository,
        private val playback: PlaybackConnection,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        preferences: LibraryPreferencesRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AlbumUiState> =
            combine(
                libraryRepository.album(albumId),
                libraryRepository.albumTracks(albumId),
                // Only the track id: position ticks and play/pause must not rebuild the list.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
                preferences.preferences.map { it.ratingMode }.distinctUntilChanged(),
            ) { album, tracks, currentTrackId, ratingMode ->
                if (album == null) {
                    AlbumUiState.NotFound
                } else {
                    val discs =
                        tracks.groupBy { it.discNumber ?: 1 }.map { (number, discTracks) ->
                            Disc(number, discTracks)
                        }
                    AlbumUiState.Content(album, discs, currentTrackId, ratingMode)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading)

        fun onAction(action: AlbumAction) {
            val tracks = (uiState.value as? AlbumUiState.Content)?.tracks.orEmpty()
            when (action) {
                AlbumAction.Play -> {
                    if (tracks.isNotEmpty()) playback.play(tracks)
                }

                // The player picks the random start and keeps the album order for unshuffling (PlaybackConnection).
                AlbumAction.Shuffle -> {
                    if (tracks.isNotEmpty()) playback.play(tracks, shuffle = true)
                }

                is AlbumAction.PlayTrack -> {
                    val index = tracks.indexOfFirst { it.id == action.trackId }
                    if (index >= 0) playback.play(tracks, startIndex = index)
                }

                is AlbumAction.PlayNext -> {
                    tracks.pick(action.trackId).takeIf { it.isNotEmpty() }?.let(playback::playNext)
                }

                is AlbumAction.AddToQueue -> {
                    tracks.pick(action.trackId).takeIf { it.isNotEmpty() }?.let(playback::addToQueue)
                }

                is AlbumAction.SetFavorite -> {
                    viewModelScope.launch { userData.setFavorite(action.id, action.isFavorite) }
                }

                // An album without tracks can still be rated.
                is AlbumAction.Rate -> {
                    viewModelScope.launch { userData.setRating(albumId, action.halfStars) }
                }

                is AlbumAction.Download -> {
                    // The whole album is downloaded as the album; one of its tracks on its own.
                    val origin = if (action.trackId == null) DownloadOrigin.Album(albumId) else DownloadOrigin.Single
                    downloads.download(tracks.pick(action.trackId), origin)
                }
            }
        }

        /** The track with [trackId], or all of them for `null`. */
        private fun List<Track>.pick(trackId: MediaId?): List<Track> = filter { trackId == null || it.id == trackId }

        @AssistedFactory
        interface Factory {
            fun create(albumId: MediaId): AlbumViewModel
        }
    }
