package io.github.kentprodan.mediatheq.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.albumOrder
import io.github.kentprodan.mediatheq.core.data.downloadAlbums
import io.github.kentprodan.mediatheq.core.data.playQueue
import io.github.kentprodan.mediatheq.core.data.playedTracks
import io.github.kentprodan.mediatheq.core.data.sortedFor
import io.github.kentprodan.mediatheq.core.data.tracksOfAlbums
import io.github.kentprodan.mediatheq.core.model.MediaId
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
        private val libraryRepository: LibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
    ) : ViewModel() {
        private val content =
            combine(
                libraryRepository.artist(artistId),
                libraryRepository.artistAlbums(artistId),
                libraryRepository.appearsOn(artistId),
                libraryRepository.artistTracks(artistId),
                libraryRepository.artistGenres(artistId),
            ) { artist, albums, appearsOn, tracks, genres ->
                artist?.let { ArtistUiState.Content(it, albums, appearsOn, tracks, emptyList(), genres) }
            }

        private val selection = MutableStateFlow(ListSelection<MediaId>())
        private val addToPlaylist = MutableStateFlow<List<MediaId>?>(null)

        private val page =
            combine(
                content,
                preferencesRepository.preferences
                    .map { it.artistPage to it.ratingMode }
                    .distinctUntilChanged(),
                // Only the track id: position ticks and play/pause must not rebuild the page.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { content, (page, ratingMode), currentTrackId ->
                content?.let {
                    val albums = it.albums.sortedFor(page.albumsSort)
                    val tracks = albumOrder(it.tracks, albums)
                    it.copy(
                        albums = albums,
                        tracks = tracks,
                        // Ties keep the page's album order.
                        playedTracks = playedTracks(tracks),
                        page = page,
                        currentTrackId = currentTrackId,
                        ratingMode = ratingMode,
                    )
                }
                    ?: ArtistUiState.NotFound
            }

        val uiState: StateFlow<ArtistUiState> =
            combine(page, selection, addToPlaylist) { page, selection, addToPlaylist ->
                // Tracks and albums can leave the page while selected.
                (page as? ArtistUiState.Content)?.let {
                    it.copy(selection = selection.retain(it.selectableIds), addToPlaylist = addToPlaylist)
                }
                    ?: page
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState.Loading)

        fun onAction(action: ArtistAction) {
            val state = uiState.value as? ArtistUiState.Content ?: return
            when (action) {
                ArtistAction.Play -> {
                    val (queue, shuffle) = playQueue(state.page.playAction, state.tracks, state.topTracks)
                    if (queue.isNotEmpty()) playback.play(queue, shuffle = shuffle)
                }

                // The player picks the random start and keeps the order for unshuffling (PlaybackConnection).
                ArtistAction.Shuffle -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks, shuffle = true)
                }

                ArtistAction.PlayNext -> {
                    viewModelScope.launch { commands.run(TopButton.PlayNext, state.tracks) }
                }

                ArtistAction.AddToQueue -> {
                    viewModelScope.launch { commands.run(TopButton.AddToQueue, state.tracks) }
                }

                ArtistAction.Download -> {
                    viewModelScope.launch { downloads.downloadAlbums(libraryRepository, state.albums.map { it.id }) }
                }

                is ArtistAction.PlayTopTrack -> {
                    val list = if (action.all) state.playedTracks else state.topTracks
                    val index = list.indexOfFirst { it.id == action.trackId }
                    if (index >= 0) viewModelScope.launch { trackTaps.tap(list, index) }
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
                    selection.value = ListSelection<MediaId>().selectAll(state.selectableIds)
                }

                ArtistAction.ClearSelection -> {
                    selection.value = ListSelection()
                }

                is ArtistAction.OnSelection -> {
                    val picked = selection.value
                    selection.value = ListSelection()
                    viewModelScope.launch {
                        val loose = picked.pick(state.topTracks) { it.id }
                        val albumIds = picked.pick(state.albums + state.appearsOn) { it.id }.map { it.id }
                        // Shown order; a top track also in a selected album comes once.
                        val tracks = (loose + libraryRepository.tracksOfAlbums(albumIds)).distinctBy { it.id }
                        when (action.command) {
                            SelectionCommand.Play -> {
                                commands.run(TopButton.Play, tracks)
                            }

                            SelectionCommand.PlayNext -> {
                                commands.run(TopButton.PlayNext, tracks)
                            }

                            SelectionCommand.AddToQueue -> {
                                commands.run(TopButton.AddToQueue, tracks)
                            }

                            SelectionCommand.Favorite -> {
                                commands.toggleFavorite(tracks)
                            }

                            SelectionCommand.AddToPlaylist -> {
                                addToPlaylist.value = tracks.map { it.id }
                            }

                            SelectionCommand.Download -> {
                                downloads.downloadAlbums(libraryRepository, albumIds)
                                commands.download(loose)
                            }
                        }
                    }
                }

                ArtistAction.AddToPlaylistShown -> {
                    addToPlaylist.value = null
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
