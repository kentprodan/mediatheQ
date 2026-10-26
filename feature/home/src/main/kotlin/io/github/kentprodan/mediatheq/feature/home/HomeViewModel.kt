package io.github.kentprodan.mediatheq.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Home tab. Sections are derived from the local library (Room), so Home needs no network and shows the cached
 * library when the server cannot be reached.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val playback: PlaybackConnection,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** The sync status, and whether a full sync ever completed (then an empty library is really empty). */
        private val sync: Flow<Pair<SyncStatus, Boolean>> =
            syncRepository.status
                .scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                    status to (previous?.second == true || status.lastSyncedAt != null)
                }.filterNotNull()

        private val sections: Flow<HomeUiState.Content?> =
            combine(libraryRepository.albums(), libraryRepository.tracks()) { albums, tracks ->
                if (albums.isEmpty() && tracks.isEmpty()) null else sections(albums, tracks)
            }.flowOn(defaultDispatcher)

        val uiState: StateFlow<HomeUiState> =
            combine(sections, sync, libraryRepository.offline) { content, (status, everSynced), offline ->
                when {
                    content != null -> content

                    // Before the sync states: offline, a failed or unfinished sync is not what is missing.
                    offline -> HomeUiState.NothingDownloaded

                    everSynced -> HomeUiState.Empty

                    status is SyncStatus.Failed -> HomeUiState.Error(status.error)

                    else -> HomeUiState.FirstSync
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

        fun onAction(action: HomeAction) {
            when (action) {
                HomeAction.ShuffleAll -> {
                    launch { playback.play(libraryRepository.tracks().first(), shuffle = true) }
                }

                is HomeAction.PlayFavorite -> {
                    launch {
                        val favorites = libraryRepository.tracks().first().filter { it.userData.isFavorite }
                        playback.play(favorites, startIndex = favorites.indexOf(action.track).coerceAtLeast(0))
                    }
                }

                is HomeAction.PlayNext -> {
                    playback.playNext(listOf(action.track))
                }

                is HomeAction.AddToQueue -> {
                    playback.addToQueue(listOf(action.track))
                }

                is HomeAction.SetFavorite -> {
                    launch { userData.setFavorite(action.track.id, action.isFavorite) }
                }

                is HomeAction.Download -> {
                    downloads.download(listOf(action.track))
                }

                HomeAction.Sync -> {
                    launch { syncRepository.sync() }
                }
            }
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        internal companion object {
            /** Albums per row section. */
            const val ROW_LIMIT = 20

            /** Favorite songs listed on Home; playing one queues all favorites. */
            const val FAVORITE_TRACKS_LIMIT = 5
        }
    }

/**
 * The sections, from [albums] (by sort name) and [tracks]. Albums count as played when any of their tracks was:
 * Jellyfin rarely sets a played date on the album itself.
 */
private fun sections(
    albums: List<Album>,
    tracks: List<Track>,
): HomeUiState.Content {
    val lastPlayed =
        tracks
            .mapNotNull { track -> track.albumId?.let { id -> track.userData.lastPlayedAt?.let { id to it } } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }
    return HomeUiState.Content(
        recentlyPlayed =
            albums
                .mapNotNull { album ->
                    listOfNotNull(lastPlayed[album.id], album.userData.lastPlayedAt).maxOrNull()?.let { album to it }
                }.sortedByDescending { it.second }
                .take(HomeViewModel.ROW_LIMIT)
                .map { it.first },
        recentlyAdded =
            albums.filter { it.dateAdded != null }.sortedByDescending { it.dateAdded }.take(HomeViewModel.ROW_LIMIT),
        favoriteAlbums = albums.filter { it.userData.isFavorite }.take(HomeViewModel.ROW_LIMIT),
        favoriteTracks = tracks.filter { it.userData.isFavorite }.take(HomeViewModel.FAVORITE_TRACKS_LIMIT),
    )
}
