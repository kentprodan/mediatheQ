package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.albumYears
import io.github.kentprodan.mediatheq.core.data.downloadAlbums
import io.github.kentprodan.mediatheq.core.data.downloadPlaylists
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.data.homeLibrary
import io.github.kentprodan.mediatheq.core.data.tracksOfAlbums
import io.github.kentprodan.mediatheq.core.data.tracksOfArtists
import io.github.kentprodan.mediatheq.core.data.tracksOfPlaylists
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.navigation.HomeSectionListKey
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LibraryViewModel.Factory::class)
class LibraryViewModel
    @AssistedInject
    constructor(
        /** The list this screen shows (#129: every Library list is a screen of its own). */
        @Assisted private val fixedFilter: LibraryFilter,
        /** A year's page (#129): the albums of that year. */
        @Assisted private val yearPage: YearKey?,
        /** A record label's page (#131): the albums of that label. */
        @Assisted private val labelPage: RecordLabelKey?,
        /** A Home section's full list (#183): its items without Home's limit, in the section's order. */
        @Assisted private val sectionPage: HomeSectionListKey?,
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        private val playlists: PlaylistRepository,
        private val shuffle: HomeShuffle,
        private val clock: Clock,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val listFilter = MutableStateFlow(LibraryListFilter())
        private val selection = MutableStateFlow(ListSelection<MediaId>())
        private val addToPlaylist = MutableStateFlow<List<MediaId>?>(null)

        // The failed sync the user already saw as a snackbar; the same failure is not shown twice.
        private val shownFailure = MutableStateFlow<SyncStatus.Failed?>(null)

        /** The list's items, sorted off the main thread; a Home section keeps its own order (#183). */
        private val items: Flow<LibraryContent> =
            itemsOf(fixedFilter)
                .combine(
                    preferencesRepository.preferences
                        .map { it.sortFor(fixedFilter) to it.isReversed(fixedFilter) }
                        .distinctUntilChanged(),
                ) { items, (sort, reversed) ->
                    items(sort).let { if (reversed && sectionPage == null) it.reversed() else it }
                }.flowOn(defaultDispatcher)

        /** [items] narrowed by the header's filter and search (#123), with the unfiltered items' presence. */
        private val shown: Flow<ShownItems> =
            combine(
                items,
                listFilter,
                combine(downloads.completedIds, libraryRepository.fullyDownloaded) { tracks, lists -> tracks + lists },
            ) { items, listFilter, downloaded ->
                ShownItems(items, items.filtered(listFilter, downloaded), listFilter)
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
                preferencesRepository.preferences,
                this.shown,
                combine(sync, libraryRepository.offline, ::Pair),
                combine(shownFailure, selection, addToPlaylist, ::Triple),
            ) {
                preferences,
                items,
                (sync, offline),
                (shown, selection, addToPlaylist),
                ->
                val (status, everSynced) = sync
                val content =
                    when {
                        items.filtered.isNotEmptyContent() -> items.filtered
                        items.all.isNotEmptyContent() -> LibraryContent.NoMatches
                        offline -> LibraryContent.NothingDownloaded
                        else -> emptyContent(status, everSynced)
                    }
                val failureShownAsContent = content is LibraryContent.Offline || content is LibraryContent.Error
                LibraryUiState(
                    filter = fixedFilter,
                    yearPage = yearPage,
                    labelPage = labelPage,
                    sectionPage = sectionPage,
                    albumColumns = preferences.albumColumns,
                    genresGrid = preferences.genresGrid,
                    sort = preferences.sortFor(fixedFilter),
                    sortReversed = preferences.isReversed(fixedFilter),
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
                    // Items can leave the list (a filter, a sync) while selected.
                    selection = selection.retain(content.ids),
                    addToPlaylist = addToPlaylist,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                LibraryUiState(fixedFilter, yearPage = yearPage, labelPage = labelPage, sectionPage = sectionPage),
            )

        fun onAction(action: LibraryAction) {
            when (action) {
                is LibraryAction.ToggleSelection -> {
                    selection.update { it.retain(uiState.value.content.ids).toggle(action.id) }
                }

                LibraryAction.SelectAll -> {
                    selection.value = ListSelection<MediaId>().selectAll(uiState.value.content.ids)
                }

                LibraryAction.ClearSelection -> {
                    selection.value = ListSelection()
                }

                is LibraryAction.OnSelection -> {
                    val state = uiState.value
                    val picked = state.content.picked(state.selection)
                    selection.value = ListSelection()
                    launch {
                        val tracks = tracksOf(picked)
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

                            SelectionCommand.Download -> {
                                when (picked) {
                                    is LibraryContent.Albums -> {
                                        downloads.downloadAlbums(libraryRepository, picked.albums.map { it.id })
                                    }

                                    is LibraryContent.Playlists -> {
                                        downloads.downloadPlaylists(playlists, picked.playlists.map { it.id })
                                    }

                                    else -> {
                                        commands.download(tracks)
                                    }
                                }
                            }

                            SelectionCommand.Favorite -> {
                                commands.toggleFavorite(tracks)
                            }

                            SelectionCommand.AddToPlaylist -> {
                                addToPlaylist.value = tracks.map { it.id }
                            }
                        }
                    }
                }

                LibraryAction.AddToPlaylistShown -> {
                    addToPlaylist.value = null
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
                    launch { preferencesRepository.setSort(fixedFilter, action.sort) }
                }

                LibraryAction.ReverseSort -> {
                    val state = uiState.value
                    launch { preferencesRepository.setSort(fixedFilter, state.sort, !state.sortReversed) }
                }

                is LibraryAction.SetAlbumColumns -> {
                    launch { preferencesRepository.setAlbumColumns(action.columns) }
                }

                is LibraryAction.SetGenresGrid -> {
                    launch { preferencesRepository.setGenresGrid(action.on) }
                }

                LibraryAction.Refresh -> {
                    // A random section's list draws a new set, as Home's pull to refresh does (owner, #183).
                    if (sectionPage != null) shuffle.reshuffle()
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
                is LibraryContent.Albums -> {
                    libraryRepository.tracksOfAlbums(content.albums.map { it.id })
                }

                is LibraryContent.Artists -> {
                    libraryRepository.tracksOfArtists(content.artists.map { it.id })
                }

                is LibraryContent.Playlists -> {
                    playlists.tracksOfPlaylists(content.playlists.map { it.id })
                }

                is LibraryContent.Songs -> {
                    content.tracks
                }

                is LibraryContent.Years -> {
                    libraryRepository.tracksOfAlbums(
                        content.years.flatMap { it.albums }.map { it.id },
                    )
                }

                // Each label's albums in list order, an album of two shown labels once.
                is LibraryContent.RecordLabels -> {
                    libraryRepository.tracksOfAlbums(
                        content.labels
                            .flatMap {
                                libraryRepository
                                    .labelAlbums(
                                        it.id,
                                    ).first()
                            }.map { it.id }
                            .distinct(),
                    )
                }

                // Each genre's tracks in list order, a track tagged with two shown genres once.
                is LibraryContent.Genres -> {
                    content.genres.flatMap { libraryRepository.genreTracks(it.id).first() }.distinctBy { it.id }
                }

                else -> {
                    emptyList()
                }
            }

        /** Items of [filter], as a function that sorts them into content. */
        private fun itemsOf(filter: LibraryFilter): Flow<Sorter> =
            if (sectionPage != null) sectionItems(sectionPage.section) else listItems(filter)

        /** A Home section's items in its own order: the sort is hidden and ignored (owner, #183). */
        private fun sectionItems(section: HomeSection): Flow<Sorter> =
            combine(libraryRepository.homeLibrary(), shuffle.seed) { library, seed ->
                val content =
                    when (val items = homeItems(section, library, Int.MAX_VALUE, seed, clock.now())) {
                        is HomeItems.Albums -> LibraryContent.Albums(items.albums)
                        is HomeItems.Artists -> LibraryContent.Artists(items.artists)
                        is HomeItems.Tracks -> LibraryContent.Songs(items.tracks)
                        is HomeItems.Playlists -> LibraryContent.Playlists(items.playlists)
                        is HomeItems.Shortcuts -> LibraryContent.Empty
                    }
                val sorter: Sorter = { _ -> content }
                sorter
            }

        private fun listItems(filter: LibraryFilter): Flow<Sorter> =
            when (filter) {
                LibraryFilter.Albums -> {
                    val albums = labelPage?.let { libraryRepository.labelAlbums(it.id) } ?: libraryRepository.albums()
                    albums.map { all ->
                        val list = if (yearPage == null) all else all.filter { it.year == yearPage.year }
                        val sorter: Sorter = { sort -> LibraryContent.Albums(list.sortedFor(sort)) }
                        sorter
                    }
                }

                LibraryFilter.RecordLabels -> {
                    libraryRepository.recordLabels().map { list ->
                        { sort -> LibraryContent.RecordLabels(list.sortedFor(sort)) }
                    }
                }

                LibraryFilter.Genres -> {
                    libraryRepository.genres().map { list ->
                        { sort -> LibraryContent.Genres(list.sortedFor(sort)) }
                    }
                }

                LibraryFilter.Years -> {
                    libraryRepository.albums().map { list ->
                        { sort -> LibraryContent.Years(albumYears(list).sortedFor(sort)) }
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
            fun create(
                fixedFilter: LibraryFilter,
                yearPage: YearKey?,
                labelPage: RecordLabelKey?,
                sectionPage: HomeSectionListKey?,
            ): LibraryViewModel
        }
    }

/** A list's items, as filtered by the list header, and the header's filter. */
private data class ShownItems(
    val all: LibraryContent,
    val filtered: LibraryContent,
    val listFilter: LibraryListFilter,
)

/** Sorts a list's items into content. */
private typealias Sorter = (LibrarySort) -> LibraryContent

private fun LibraryContent.isNotEmptyContent(): Boolean =
    when (this) {
        is LibraryContent.Albums -> albums.isNotEmpty()
        is LibraryContent.Artists -> artists.isNotEmpty()
        is LibraryContent.Playlists -> playlists.isNotEmpty()
        is LibraryContent.Songs -> tracks.isNotEmpty()
        is LibraryContent.Years -> years.isNotEmpty()
        is LibraryContent.Genres -> genres.isNotEmpty()
        is LibraryContent.RecordLabels -> labels.isNotEmpty()
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
