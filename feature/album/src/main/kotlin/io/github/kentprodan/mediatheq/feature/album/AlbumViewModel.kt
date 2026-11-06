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
import io.github.kentprodan.mediatheq.core.data.MoreAlbumsSort
import io.github.kentprodan.mediatheq.core.data.ResumeRepository
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.resumeTarget
import io.github.kentprodan.mediatheq.core.data.sortedFor
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel
    @AssistedInject
    constructor(
        @Assisted private val albumId: MediaId,
        libraryRepository: LibraryRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        preferences: LibraryPreferencesRepository,
        private val resumes: ResumeRepository,
    ) : ViewModel() {
        private val selection = MutableStateFlow(ListSelection<MediaId>())

        private val album = libraryRepository.album(albumId)
        private val tracks = libraryRepository.albumTracks(albumId)
        private val pagePreferences =
            preferences.preferences
                .map { it.ratingMode to it.albumPage }
                .distinctUntilChanged()

        /** The first album artist's other albums (offline: downloaded ones), sorted as the page settings say. */
        private val moreAlbums =
            combine(
                album
                    .map { it?.albumArtists?.firstOrNull()?.id }
                    .distinctUntilChanged()
                    .flatMapLatest { artistId ->
                        artistId?.let(libraryRepository::artistAlbums) ?: flowOf(emptyList())
                    },
                pagePreferences.map { it.second.moreAlbumsSort }.distinctUntilChanged(),
            ) { albums, sort -> albums.filter { it.id != albumId }.sortedFor(sort) }

        private val about =
            combine(libraryRepository.albumGenres(albumId), libraryRepository.albumLabels(albumId)) { genres, labels ->
                genres to labels
            }

        private val page =
            combine(
                combine(album, tracks, ::Pair),
                // Only the track id: position ticks and play/pause must not rebuild the list.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
                pagePreferences,
                combine(moreAlbums, about, resumes.resume(albumId), ::Triple),
            ) { (album, tracks), currentTrackId, (ratingMode, albumPage), (more, about, resume) ->
                if (album == null) {
                    AlbumUiState.NotFound
                } else {
                    val discs =
                        tracks.groupBy { it.discNumber ?: 1 }.map { (number, discTracks) ->
                            Disc(number, discTracks)
                        }
                    AlbumUiState.Content(
                        album,
                        discs,
                        currentTrackId,
                        ratingMode,
                        page = albumPage,
                        resume = resumeTarget(resume, tracks),
                        moreAlbums = more,
                        genres = about.first,
                        labels = about.second,
                    )
                }
            }

        val uiState: StateFlow<AlbumUiState> =
            combine(page, selection) { page, selection ->
                // Tracks can leave the page while selected.
                (page as? AlbumUiState.Content)?.let {
                    it.copy(
                        selection =
                            selection.retain(
                                it.tracks.map { t ->
                                    t.id
                                },
                            ),
                    )
                }
                    ?: page
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading)

        fun onAction(action: AlbumAction) {
            val tracks = (uiState.value as? AlbumUiState.Content)?.tracks.orEmpty()
            when (action) {
                AlbumAction.Play -> {
                    if (tracks.isNotEmpty()) playback.play(tracks)
                }

                AlbumAction.Resume -> {
                    val resume = (uiState.value as? AlbumUiState.Content)?.resume
                    val index = tracks.indexOfFirst { it.id == resume?.trackId }
                    if (resume != null && index >= 0) playback.play(tracks, index, startPosition = resume.position)
                }

                // The player picks the random start and keeps the album order for unshuffling (PlaybackConnection).
                AlbumAction.Shuffle -> {
                    if (tracks.isNotEmpty()) playback.play(tracks, shuffle = true)
                }

                is AlbumAction.PlayTrack -> {
                    val index = tracks.indexOfFirst { it.id == action.trackId }
                    viewModelScope.launch { trackTaps.tap(tracks, index) }
                }

                is AlbumAction.PlayNext -> {
                    tracks.pick(action.trackId).takeIf { it.isNotEmpty() }?.let(playback::playNext)
                }

                is AlbumAction.AddToQueue -> {
                    tracks.pick(action.trackId).takeIf { it.isNotEmpty() }?.let(playback::addToQueue)
                }

                is AlbumAction.SwipeTrack -> {
                    viewModelScope.launch {
                        commands.swipe(action.track, action.right)
                    }
                }

                is AlbumAction.RateTrack -> {
                    viewModelScope.launch {
                        commands.rate(action.track, action.halfStars)
                    }
                }

                is AlbumAction.ToggleSelection -> {
                    selection.update { it.toggle(action.id) }
                }

                AlbumAction.SelectAll -> {
                    selection.value =
                        ListSelection<MediaId>().selectAll(
                            ((uiState.value as? AlbumUiState.Content)?.tracks.orEmpty()).map { it.id },
                        )
                }

                AlbumAction.ClearSelection -> {
                    selection.value = ListSelection()
                }

                is AlbumAction.OnSelection -> {
                    val picked =
                        selection.value.pick(
                            (uiState.value as? AlbumUiState.Content)?.tracks.orEmpty(),
                        ) { it.id }
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
