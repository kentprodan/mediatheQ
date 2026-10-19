package io.github.kentprodan.mediatheq.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel
    @AssistedInject
    constructor(
        @Assisted albumId: MediaId,
        libraryRepository: LibraryRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<AlbumUiState> =
            combine(libraryRepository.album(albumId), libraryRepository.albumTracks(albumId)) { album, tracks ->
                if (album == null) {
                    AlbumUiState.NotFound
                } else {
                    val discs =
                        tracks.groupBy { it.discNumber ?: 1 }.map { (number, discTracks) ->
                            Disc(number, discTracks)
                        }
                    AlbumUiState.Content(album, discs)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading)

        fun onAction(action: AlbumAction) {
            val tracks = (uiState.value as? AlbumUiState.Content)?.tracks.orEmpty()
            if (tracks.isEmpty()) return
            when (action) {
                AlbumAction.Play -> {
                    playback.play(tracks)
                }

                // The player picks the random start and keeps the album order for unshuffling (PlaybackConnection).
                AlbumAction.Shuffle -> {
                    playback.play(tracks, shuffle = true)
                }

                is AlbumAction.PlayTrack -> {
                    val index = tracks.indexOfFirst { it.id == action.trackId }
                    if (index >= 0) playback.play(tracks, startIndex = index)
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(albumId: MediaId): AlbumViewModel
        }
    }
