package io.github.kentprodan.mediatheq.feature.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
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
        private val commands: TrackCommands,
    ) : ViewModel() {
        private val content =
            combine(
                libraryRepository.genre(genreId),
                libraryRepository.genreAlbums(genreId),
                libraryRepository.genreArtists(genreId),
                libraryRepository.genreTracks(genreId),
                libraryRepository.genreTrackArtists(genreId),
            ) { genre, albums, albumArtists, tracks, trackArtists ->
                genre?.let { GenreUiState.Content(it, albums, albumArtists, tracks, trackArtists) }
            }

        val uiState: StateFlow<GenreUiState> =
            combine(
                content,
                preferencesRepository.preferences.map { it.genrePage to it.albumColumns }.distinctUntilChanged(),
                // Only the track id: position ticks and play/pause must not rebuild the page.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { content, (page, columns), current ->
                content?.copy(page = page, albumColumns = columns, currentTrackId = current) ?: GenreUiState.NotFound
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

                GenreAction.PlayNext -> {
                    viewModelScope.launch { commands.run(TopButton.PlayNext, state.tracks) }
                }

                GenreAction.AddToQueue -> {
                    viewModelScope.launch { commands.run(TopButton.AddToQueue, state.tracks) }
                }

                is GenreAction.PlayTrack -> {
                    val list = if (action.all) state.rankedTracks else state.topTracks
                    val index = list.indexOfFirst { it.id == action.track.id }
                    if (index >= 0) viewModelScope.launch { trackTaps.tap(list, index) }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(genreId: MediaId): GenreViewModel
        }
    }
