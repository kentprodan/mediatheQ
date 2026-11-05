package io.github.kentprodan.mediatheq.feature.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GenreViewModel.Factory::class)
class GenreViewModel
    @AssistedInject
    constructor(
        @Assisted private val genreId: MediaId,
        libraryRepository: LibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
    ) : ViewModel() {
        private val allTracks = MutableStateFlow(false)

        private val content =
            combine(
                libraryRepository.genre(genreId),
                libraryRepository.genreAlbums(genreId),
                libraryRepository.genreArtists(genreId),
                libraryRepository.genreTracks(genreId),
            ) { genre, albums, artists, tracks -> genre?.let { GenreUiState.Content(it, albums, artists, tracks) } }

        val uiState: StateFlow<GenreUiState> =
            combine(
                content,
                allTracks,
                preferencesRepository.preferences.map { it.albumColumns }.distinctUntilChanged(),
                // Only the track id: position ticks and play/pause must not rebuild the page.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { content, all, columns, current ->
                content?.copy(allTracks = all, albumColumns = columns, currentTrackId = current)
                    ?: GenreUiState.NotFound
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreUiState.Loading)

        fun onAction(action: GenreAction) {
            val state = uiState.value as? GenreUiState.Content ?: return
            when (action) {
                GenreAction.Play -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks)
                }

                // The player picks the random start and keeps the order for unshuffling (PlaybackConnection).
                GenreAction.Shuffle -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks, shuffle = true)
                }

                is GenreAction.PlayTrack -> {
                    viewModelScope.launch { trackTaps.tap(state.tracks, state.tracks.indexOf(action.track)) }
                }

                GenreAction.ShowAllTracks -> {
                    allTracks.value = true
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(genreId: MediaId): GenreViewModel
        }
    }
