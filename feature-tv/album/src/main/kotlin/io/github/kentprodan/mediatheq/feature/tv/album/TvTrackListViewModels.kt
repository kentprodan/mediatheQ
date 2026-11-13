package io.github.kentprodan.mediatheq.feature.tv.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.albumQuality
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.ResumeRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.resumeTarget
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What album and playlist pages share (#238): the actions on the page's tracks. [pageId] is the album or playlist
 * (its favorite); [playlistId] is set on a playlist page, so its plays record into the playlist's Resume (#135).
 */
abstract class TvTrackListViewModel(
    private val pageId: MediaId,
    private val playlistId: MediaId?,
    private val playback: PlaybackConnection,
    private val trackTaps: TrackTaps,
    private val userData: UserDataRepository,
) : ViewModel() {
    protected abstract val page: Flow<TvTrackListUiState>

    /** The track that plays now; only its id, so position ticks do not rebuild the page. */
    protected val currentTrackId: Flow<MediaId?> = playback.state.map { it.item?.id }.distinctUntilChanged()

    val uiState: StateFlow<TvTrackListUiState> by lazy {
        page.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvTrackListUiState.Loading)
    }

    fun onAction(action: TvTrackListAction) {
        val content = uiState.value as? TvTrackListUiState.Content ?: return
        val tracks = content.tracks
        when (action) {
            TvTrackListAction.Play -> {
                if (tracks.isNotEmpty()) playback.play(tracks, playlistId = playlistId)
            }

            // The player picks the random start and keeps the order for unshuffling (PlaybackConnection).
            TvTrackListAction.Shuffle -> {
                if (tracks.isNotEmpty()) playback.play(tracks, shuffle = true, playlistId = playlistId)
            }

            TvTrackListAction.Resume -> {
                val resume = content.resume ?: return
                val index = tracks.indexOfFirst { it.id == resume.trackId }
                if (index >= 0) playback.play(tracks, index, startPosition = resume.position, playlistId = playlistId)
            }

            is TvTrackListAction.PlayTrack -> {
                viewModelScope.launch { trackTaps.tap(tracks, action.index, playlistId) }
            }

            is TvTrackListAction.PlayNext -> {
                playback.playNext(listOf(action.track))
            }

            is TvTrackListAction.AddToQueue -> {
                playback.addToQueue(listOf(action.track))
            }

            // Optimistic, as on the phone: the repository updates the library at once and syncs to the server.
            TvTrackListAction.ToggleFavorite -> {
                viewModelScope.launch { userData.setFavorite(pageId, !content.header.isFavorite) }
            }

            is TvTrackListAction.ToggleTrackFavorite -> {
                viewModelScope.launch { userData.setFavorite(action.track.id, !action.track.userData.isFavorite) }
            }
        }
    }
}

/** An album page on TV (#238): the album's tracks by disc, its best quality, Resume where it was left (#132). */
@HiltViewModel(assistedFactory = TvAlbumViewModel.Factory::class)
class TvAlbumViewModel
    @AssistedInject
    constructor(
        @Assisted albumId: MediaId,
        library: LibraryRepository,
        resumes: ResumeRepository,
        playback: PlaybackConnection,
        trackTaps: TrackTaps,
        userData: UserDataRepository,
    ) : TvTrackListViewModel(albumId, null, playback, trackTaps, userData) {
        override val page: Flow<TvTrackListUiState> =
            combine(
                library.album(albumId),
                library.albumTracks(albumId),
                currentTrackId,
                resumes.resume(albumId),
            ) { album, tracks, current, resume ->
                if (album == null) return@combine TvTrackListUiState.NotFound
                TvTrackListUiState.Content(
                    TvTrackListHeader(
                        album.title,
                        album.albumArtists.joinToString { it.name }.ifEmpty { null },
                        album.year,
                        album.artwork,
                        tracks.size,
                        tracks.fold(kotlin.time.Duration.ZERO) { sum, track -> sum + track.duration },
                        albumQuality(tracks),
                        album.userData.isFavorite,
                    ),
                    tracks.groupBy { it.discNumber ?: 1 }.map { (number, disc) -> TvDisc(number, disc) },
                    current,
                    resumeTarget(resume, tracks),
                )
            }

        @AssistedFactory
        interface Factory {
            fun create(albumId: MediaId): TvAlbumViewModel
        }
    }

/** A playlist page on TV (#238): its entries in order, Resume from its own plays (#135); no editing on TV 1. */
@HiltViewModel(assistedFactory = TvPlaylistViewModel.Factory::class)
class TvPlaylistViewModel
    @AssistedInject
    constructor(
        @Assisted playlistId: MediaId,
        playlists: PlaylistRepository,
        resumes: ResumeRepository,
        playback: PlaybackConnection,
        trackTaps: TrackTaps,
        userData: UserDataRepository,
    ) : TvTrackListViewModel(playlistId, playlistId, playback, trackTaps, userData) {
        override val page: Flow<TvTrackListUiState> =
            combine(
                playlists.playlist(playlistId),
                playlists.items(playlistId),
                currentTrackId,
                resumes.playlistResume(playlistId),
            ) { playlist, items, current, resume ->
                if (playlist == null) return@combine TvTrackListUiState.NotFound
                val tracks = items.map { it.track }
                TvTrackListUiState.Content(
                    TvTrackListHeader(
                        playlist.name,
                        artwork = playlist.artwork,
                        trackCount = tracks.size,
                        duration = tracks.fold(kotlin.time.Duration.ZERO) { sum, track -> sum + track.duration },
                        isFavorite = playlist.userData.isFavorite,
                    ),
                    listOf(TvDisc(1, tracks)),
                    current,
                    resumeTarget(resume, tracks),
                )
            }

        @AssistedFactory
        interface Factory {
            fun create(playlistId: MediaId): TvPlaylistViewModel
        }
    }
