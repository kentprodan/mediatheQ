package io.github.kentprodan.mediatheq.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.HomeLibrary
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.HomeVideos
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.data.homeLibrary
import io.github.kentprodan.mediatheq.core.data.mixTracks
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
@OptIn(ExperimentalCoroutinesApi::class)
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
        private val playlists: PlaylistRepository,
        preferences: LibraryPreferencesRepository,
        private val shuffle: HomeShuffle,
        private val appMode: AppModeRepository,
        private val videos: VideoRepository,
        private val clock: Clock,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** The sync status, and whether a full sync ever completed (then an empty library is really empty). */
        private val sync: Flow<Pair<SyncStatus, Boolean>> =
            syncRepository.status
                .scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                    status to (previous?.second == true || status.lastSyncedAt != null)
                }.filterNotNull()

        /** The video sections' items (#275), read only while the app mode has video. */
        private val homeVideos: Flow<HomeVideos> =
            appMode.mode.map { it.video }.distinctUntilChanged().flatMapLatest { video ->
                if (!video) {
                    flowOf(HomeVideos())
                } else {
                    combine(
                        videos.continueWatching(VIDEO_SECTION_LIMIT),
                        videos.nextUpEpisodes(VIDEO_SECTION_LIMIT),
                        videos.recentlyAdded(VideoKind.Movie, VIDEO_SECTION_LIMIT),
                        videos.recentlyAdded(VideoKind.Episode, VIDEO_SECTION_LIMIT),
                        ::HomeVideos,
                    )
                }
            }

        private val library: Flow<HomeLibrary> =
            combine(libraryRepository.homeLibrary(), homeVideos) { library, videos -> library.copy(videos = videos) }

        /** The mode toggle's value; `null` hides it (a server without video is always Music, ADR 0051). */
        val modeToggle: StateFlow<AppMode?> =
            (if (appMode.videoAvailable) appMode.mode else flowOf(null))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** `null` while the library is empty (the empty and sync states decide what shows). */
        private val sections: Flow<List<HomeSectionContent>?> =
            combine(
                library,
                preferences.preferences.map { it.home }.distinctUntilChanged(),
                shuffle.seed,
                appMode.mode,
            ) { library, home, seed, mode ->
                // An empty music library leaves Home to its empty states; Video mode has no music to wait for.
                val noMusic = library.albums.isEmpty() && library.tracks.isEmpty()
                val noVideo = library.videos == HomeVideos()
                if (mode.music && noMusic && (!mode.video || noVideo)) return@combine null
                val now = clock.now()
                home.shown.filter { it.allowed(mode) }.mapNotNull { section ->
                    val settings = home.settingsOf(section)
                    val items = homeItems(section, library, settings.maxItems, seed, now)
                    HomeSectionContent(section, settings, items).takeUnless { items.isEmpty && settings.hideIfEmpty }
                }
            }.flowOn(defaultDispatcher)

        val uiState: StateFlow<HomeUiState> =
            combine(
                sections,
                sync,
                libraryRepository.offline,
                appMode.mode,
            ) { sections, (status, everSynced), offline, mode ->
                when {
                    // Video mode with nothing to continue, next up or newly added.
                    !mode.music && sections.isNullOrEmpty() -> {
                        HomeUiState.VideoMode
                    }

                    sections != null -> {
                        HomeUiState.Content(
                            sections,
                            refreshing = status is SyncStatus.Running,
                            showShuffle = mode.music,
                        )
                    }

                    // Before the sync states: offline, a failed or unfinished sync is not what is missing.
                    offline -> {
                        HomeUiState.NothingDownloaded
                    }

                    everSynced -> {
                        HomeUiState.Empty
                    }

                    status is SyncStatus.Failed -> {
                        HomeUiState.Error(status.error)
                    }

                    else -> {
                        HomeUiState.FirstSync
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

        init {
            // Continue watching as the server has it now, not as of the last sync (#275); best effort.
            viewModelScope.launch { if (appMode.mode.first().video) videos.refreshResume() }
        }

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

                // A cover's play button (#182): the collection in its own order, like its page's Play.
                is HomeAction.PlayAlbum -> {
                    launch { play(libraryRepository.albumTracks(action.id).first()) }
                }

                is HomeAction.PlayPlaylist -> {
                    launch { play(playlists.items(action.id).first().map { it.track }) }
                }

                is HomeAction.PlayArtist -> {
                    launch {
                        val albums = libraryRepository.artistAlbums(action.id).first()
                        play(albums.flatMap { libraryRepository.albumTracks(it.id).first() })
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

                is HomeAction.SetMode -> {
                    launch { appMode.setMode(action.mode) }
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

        private fun play(tracks: List<Track>) {
            if (tracks.isNotEmpty()) playback.play(tracks)
        }
    }

/** At most this many items per video section (#275); the section's own max cuts further. */
private const val VIDEO_SECTION_LIMIT = 50
