package io.github.kentprodan.mediatheq.feature.tv.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.ArtistPlayAction
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.albumOrder
import io.github.kentprodan.mediatheq.core.data.playQueue
import io.github.kentprodan.mediatheq.core.data.playedTracks
import io.github.kentprodan.mediatheq.core.data.sortedFor
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The artist page on TV (#239). */
sealed interface TvArtistUiState {
    data object Loading : TvArtistUiState

    /** The artist is not in the library (removed on the server). */
    data object NotFound : TvArtistUiState

    data class Content(
        val artist: Artist,
        /** Their own albums, in the artist page's sort. */
        val albums: List<Album>,
        /** Albums of others they are credited on (R16). */
        val appearsOn: List<Album>,
        /** Every track, own albums first in [albums]' order: what Play all and Shuffle all queue. */
        val tracks: List<Track>,
        /** The most played tracks, as many as the page settings say. */
        val topTracks: List<Track>,
        val playAction: ArtistPlayAction = ArtistPlayAction.Albums,
        val currentTrackId: MediaId? = null,
    ) : TvArtistUiState {
        val isEmpty: Boolean get() = albums.isEmpty() && appearsOn.isEmpty() && tracks.isEmpty()
    }
}

sealed interface TvArtistAction {
    /** Play all: the artist page's Play action (#133), albums in order by default. */
    data object PlayAll : TvArtistAction

    data object ShuffleAll : TvArtistAction

    /** Select on a top track: the top tracks from it. */
    data class PlayTopTrack(
        val index: Int,
    ) : TvArtistAction
}

/** The artist page on TV (#239): the same queries, sorts and queues as the phone's artist page. */
@HiltViewModel(assistedFactory = TvArtistViewModel.Factory::class)
class TvArtistViewModel
    @AssistedInject
    constructor(
        @Assisted artistId: MediaId,
        library: LibraryRepository,
        preferences: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
    ) : ViewModel() {
        private val artist =
            combine(
                library.artist(artistId),
                library.artistAlbums(artistId),
                library.appearsOn(artistId),
                library.artistTracks(artistId),
                ::Quad,
            )

        val uiState: StateFlow<TvArtistUiState> =
            combine(
                artist,
                preferences.preferences.map { it.artistPage }.distinctUntilChanged(),
                playback.state.map { it.item?.id }.distinctUntilChanged(),
            ) { (artist, albums, appearsOn, tracks), page, current ->
                artist ?: return@combine TvArtistUiState.NotFound
                val sorted = albums.sortedFor(page.albumsSort)
                val ordered = albumOrder(tracks, sorted)
                TvArtistUiState.Content(
                    artist,
                    sorted,
                    // The page settings can hide these sections, as on the phone.
                    if (page.showAppearsOn) appearsOn else emptyList(),
                    ordered,
                    if (page.showTopTracks) playedTracks(ordered).take(page.topTracksCount) else emptyList(),
                    page.playAction,
                    current,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvArtistUiState.Loading)

        fun onAction(action: TvArtistAction) {
            val state = uiState.value as? TvArtistUiState.Content ?: return
            when (action) {
                TvArtistAction.PlayAll -> {
                    val (queue, shuffle) = playQueue(state.playAction, state.tracks, state.topTracks)
                    if (queue.isNotEmpty()) playback.play(queue, shuffle = shuffle)
                }

                TvArtistAction.ShuffleAll -> {
                    if (state.tracks.isNotEmpty()) playback.play(state.tracks, shuffle = true)
                }

                is TvArtistAction.PlayTopTrack -> {
                    viewModelScope.launch { trackTaps.tap(state.topTracks, action.index) }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(artistId: MediaId): TvArtistViewModel
        }
    }

private data class Quad(
    val artist: Artist?,
    val albums: List<Album>,
    val appearsOn: List<Album>,
    val tracks: List<Track>,
)
