package io.github.kentprodan.mediatheq.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.HomeLibrary
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.data.mixTracks
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Clock

/**
 * The Home tab. Sections (#127) are computed from the local library (Room), so Home needs no network and shows the
 * cached library when the server cannot be reached.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        preferences: LibraryPreferencesRepository,
        private val shuffle: HomeShuffle,
        private val clock: Clock,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** The sync status, and whether a full sync ever completed (then an empty library is really empty). */
        private val sync: Flow<Pair<SyncStatus, Boolean>> =
            syncRepository.status
                .scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                    status to (previous?.second == true || status.lastSyncedAt != null)
                }.filterNotNull()

        private val library: Flow<HomeLibrary> =
            combine(
                libraryRepository.albums(),
                libraryRepository.albumArtists(),
                libraryRepository.tracks(),
                libraryRepository.allTracks(),
                libraryRepository.playlists(),
            ) { albums, artists, tracks, allTracks, playlists ->
                HomeLibrary(
                    albums,
                    artists,
                    tracks,
                    allTracks,
                    playlists,
                )
            }

        /** `null` while the library is empty (the empty and sync states decide what shows). */
        private val sections: Flow<List<HomeSectionContent>?> =
            combine(
                library,
                preferences.preferences.map { it.home }.distinctUntilChanged(),
                shuffle.seed,
            ) { library, home, seed ->
                if (library.albums.isEmpty() && library.tracks.isEmpty()) return@combine null
                val now = clock.now()
                home.shown.mapNotNull { section ->
                    val settings = section.defaultSettings
                    val items = homeItems(section, library, settings.maxItems, seed, now)
                    HomeSectionContent(section, settings, items).takeUnless { items.isEmpty && settings.hideIfEmpty }
                }
            }.flowOn(defaultDispatcher)

        val uiState: StateFlow<HomeUiState> =
            combine(sections, sync, libraryRepository.offline) { sections, (status, everSynced), offline ->
                when {
                    sections != null -> HomeUiState.Content(sections, refreshing = status is SyncStatus.Running)

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

                is HomeAction.PlayMix -> {
                    launch {
                        val queue =
                            withContext(defaultDispatcher) {
                                val tracks = libraryRepository.tracks().first()
                                mixTracks(
                                    action.mix,
                                    tracks,
                                    libraryRepository.albums().first(),
                                    shuffle.mixRandom(),
                                    clock.now(),
                                )
                            }
                        if (queue.isNotEmpty()) playback.play(queue)
                    }
                }

                is HomeAction.PlayTracks -> {
                    launch { trackTaps.tap(action.tracks, action.index) }
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

                HomeAction.Refresh -> {
                    shuffle.reshuffle()
                    // Offline there is no server to ask: the new random sets are the refresh.
                    launch { if (!libraryRepository.offline.first()) syncRepository.sync() }
                }
            }
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }
    }
