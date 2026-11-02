package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.tracksOfAlbums
import io.github.kentprodan.mediatheq.core.data.tracksOfArtists
import io.github.kentprodan.mediatheq.core.data.tracksOfPlaylists
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LibraryViewModel.Factory::class)
class LibraryViewModel
    @AssistedInject
    constructor(
        /** Set when the screen is a navigation entry of its own (Albums, Tracks, ..., #122); `null` for Library. */
        @Assisted private val fixedFilter: LibraryFilter?,
        private val savedStateHandle: SavedStateHandle,
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        private val playlists: PlaylistRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        // Saved, so the tab (and its restored scroll position) comes back after process death.
        private val filter: StateFlow<LibraryFilter> =
            fixedFilter?.let { MutableStateFlow(it) } ?: savedStateHandle.getStateFlow(FILTER_KEY, LibraryFilter.Albums)

        private val listFilter = MutableStateFlow(LibraryListFilter())

        // The failed sync the user already saw as a snackbar; the same failure is not shown twice.
        private val shownFailure = MutableStateFlow<SyncStatus.Failed?>(null)

        /**
         * The current tab's items, sorted off the main thread, tagged with their tab: right after a tab switch the
         * previous tab's items must not show under the new tab's chip. Only the open tab's items are loaded.
         */
        private val items: Flow<Pair<LibraryFilter, LibraryContent>> =
            filter
                .flatMapLatest { filter ->
                    val sort = preferencesRepository.preferences.map { it.sortFor(filter) }.distinctUntilChanged()
                    itemsOf(filter).combine(sort) { items, sort -> filter to items(sort) }
                }.flowOn(defaultDispatcher)

        /** [items] narrowed by the header's filter and search (#123), with the unfiltered items' presence. */
        private val shown: Flow<ShownItems> =
            combine(
                items,
                listFilter,
                combine(downloads.completedIds, libraryRepository.fullyDownloaded) { tracks, lists -> tracks + lists },
            ) { (filter, items), listFilter, downloaded ->
                ShownItems(filter, items, items.filtered(listFilter, downloaded), listFilter)
            }.flowOn(defaultDispatcher)

        /** The sync status, and whether a full sync ever completed (then an empty tab is really empty). */
        private val sync: Flow<Pair<SyncStatus, Boolean>> =
            syncRepository.status
                .scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                    status to (previous?.second == true || status.lastSyncedAt != null)
                }.filterNotNull()

        init {
            // A new sync makes its own failure worth showing again, even if it equals the previous one.
            viewModelScope.launch {
                syncRepository.status.filterIsInstance<SyncStatus.Running>().collect { shownFailure.value = null }
            }
        }

        val uiState: StateFlow<LibraryUiState> =
            combine(
                filter,
                preferencesRepository.preferences,
                this.shown,
                combine(sync, libraryRepository.offline, ::Pair),
                shownFailure,
            ) {
                filter,
                preferences,
                items,
                (sync, offline),
                shown,
                ->
                val (status, everSynced) = sync
                val content =
                    when {
                        items.tab != filter -> LibraryContent.Loading
                        items.filtered.isNotEmptyContent() -> items.filtered
                        items.all.isNotEmptyContent() -> LibraryContent.NoMatches
                        offline -> LibraryContent.NothingDownloaded
                        else -> emptyContent(status, everSynced)
                    }
                val failureShownAsContent = content is LibraryContent.Offline || content is LibraryContent.Error
                LibraryUiState(
                    filter = filter,
                    filterFixed = fixedFilter != null,
                    filterStyle = preferences.filterStyle,
                    albumColumns = preferences.albumColumns,
                    sort = preferences.sortFor(filter),
                    content = content,
                    refreshing = status is SyncStatus.Running,
                    // Offline mode shows downloads: a sync failing for lack of network is expected, not news.
                    refreshError =
                        (status as? SyncStatus.Failed)
                            ?.takeIf { !failureShownAsContent && !offline && it != shown }
                            ?.error,
                    header = preferences.mediaLists,
                    listFilter = items.listFilter,
                    itemCount = content.itemCount,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                // The restored tab, not the default one, from the first frame (its top bar differs).
                LibraryUiState(filter = filter.value, filterFixed = fixedFilter != null, filterStyle = null),
            )

        fun onAction(action: LibraryAction) {
            when (action) {
                is LibraryAction.SelectFilter -> {
                    if (fixedFilter == null) savedStateHandle[FILTER_KEY] = action.filter
                }

                is LibraryAction.SetFavoritesOnly -> {
                    listFilter.update { it.copy(favoritesOnly = action.on) }
                }

                is LibraryAction.SetDownloadedOnly -> {
                    listFilter.update { it.copy(downloadedOnly = action.on) }
                }

                is LibraryAction.Search -> {
                    listFilter.update { it.copy(search = action.text) }
                }

                is LibraryAction.Top -> {
                    val content = uiState.value.content
                    launch { commands.run(action.button, tracksOf(content)) }
                }

                is LibraryAction.SelectSort -> {
                    launch { preferencesRepository.setSort(filter.value, action.sort) }
                }

                is LibraryAction.SetAlbumColumns -> {
                    launch { preferencesRepository.setAlbumColumns(action.columns) }
                }

                is LibraryAction.SetFilterStyle -> {
                    launch { preferencesRepository.setFilterStyle(action.style) }
                }

                LibraryAction.Refresh -> {
                    launch { syncRepository.sync() }
                }

                LibraryAction.ErrorShown -> {
                    shownFailure.value = syncRepository.status.value as? SyncStatus.Failed
                }

                is LibraryAction.PlayTrack -> {
                    // The whole sorted list becomes the queue with Play (owner, #122; under 10k tracks).
                    val songs = (uiState.value.content as? LibraryContent.Songs)?.tracks.orEmpty()
                    launch { trackTaps.tap(songs, songs.indexOf(action.track)) }
                }

                is LibraryAction.PlayNext -> {
                    playback.playNext(listOf(action.track))
                }

                is LibraryAction.AddToQueue -> {
                    playback.addToQueue(listOf(action.track))
                }

                is LibraryAction.SetFavorite -> {
                    launch { userData.setFavorite(action.track.id, action.isFavorite) }
                }

                is LibraryAction.Download -> {
                    downloads.download(listOf(action.track))
                }

                is LibraryAction.SwipeTrack -> {
                    launch { commands.swipe(action.track, action.right) }
                }

                is LibraryAction.RateTrack -> {
                    launch { commands.rate(action.track, action.halfStars) }
                }
            }
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        /** The tracks of the shown list, in its order: albums and playlists in their order, artists' own. */
        private suspend fun tracksOf(content: LibraryContent): List<Track> =
            when (content) {
                is LibraryContent.Albums -> libraryRepository.tracksOfAlbums(content.albums.map { it.id })
                is LibraryContent.Artists -> libraryRepository.tracksOfArtists(content.artists.map { it.id })
                is LibraryContent.Playlists -> playlists.tracksOfPlaylists(content.playlists.map { it.id })
                is LibraryContent.Songs -> content.tracks
                else -> emptyList()
            }

        /** Items of [filter], as a function that sorts them into content. */
        private fun itemsOf(filter: LibraryFilter): Flow<Sorter> =
            when (filter) {
                LibraryFilter.Albums -> {
                    libraryRepository.albums().map { list ->
                        { sort -> LibraryContent.Albums(list.sortedFor(sort)) }
                    }
                }

                LibraryFilter.Artists -> {
                    libraryRepository.albumArtists().map { list ->
                        { _ -> LibraryContent.Artists(list.sortedByName()) }
                    }
                }

                LibraryFilter.Playlists -> {
                    libraryRepository.playlists().map { list ->
                        { sort -> LibraryContent.Playlists(list.sortedFor(sort)) }
                    }
                }

                LibraryFilter.Songs -> {
                    libraryRepository.tracks().map { list ->
                        { sort -> LibraryContent.Songs(list.sortedFor(sort)) }
                    }
                }
            }

        @AssistedFactory
        interface Factory {
            fun create(fixedFilter: LibraryFilter?): LibraryViewModel
        }
    }

/** A tab's items, as filtered by the list header, and the header's filter. */
private data class ShownItems(
    val tab: LibraryFilter,
    val all: LibraryContent,
    val filtered: LibraryContent,
    val listFilter: LibraryListFilter,
)

/** Sorts a tab's items into content. */
private typealias Sorter = (LibrarySort) -> LibraryContent

private fun LibraryContent.isNotEmptyContent(): Boolean =
    when (this) {
        is LibraryContent.Albums -> albums.isNotEmpty()
        is LibraryContent.Artists -> artists.isNotEmpty()
        is LibraryContent.Playlists -> playlists.isNotEmpty()
        is LibraryContent.Songs -> tracks.isNotEmpty()
        else -> false
    }

/**
 * What an empty tab shows. Once a full sync has completed, an empty tab is simply empty (a later failed sync shows as a
 * snackbar instead). Before that: loading while syncing or waiting, offline or error when the first sync failed.
 */
private fun emptyContent(
    status: SyncStatus,
    everSynced: Boolean,
): LibraryContent =
    when {
        everSynced -> LibraryContent.Empty
        status is SyncStatus.Failed && status.error == MqError.Network -> LibraryContent.Offline
        status is SyncStatus.Failed -> LibraryContent.Error(status.error)
        else -> LibraryContent.Loading
    }

private const val FILTER_KEY = "filter"
