package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val preferencesRepository: LibraryPreferencesRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        // Saved, so the tab (and its restored scroll position) comes back after process death.
        private val filter = savedStateHandle.getStateFlow(FILTER_KEY, LibraryFilter.Albums)

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
            combine(filter, preferencesRepository.preferences, items, sync, shownFailure) {
                filter,
                preferences,
                (itemsFilter, items),
                (status, everSynced),
                shown,
                ->
                val content =
                    when {
                        itemsFilter != filter -> LibraryContent.Loading
                        items.isNotEmptyContent() -> items
                        else -> emptyContent(status, everSynced)
                    }
                val failureShownAsContent = content is LibraryContent.Offline || content is LibraryContent.Error
                LibraryUiState(
                    filter = filter,
                    filterStyle = preferences.filterStyle,
                    albumColumns = preferences.albumColumns,
                    sort = preferences.sortFor(filter),
                    content = content,
                    refreshing = status is SyncStatus.Running,
                    refreshError =
                        (status as? SyncStatus.Failed)?.takeIf { !failureShownAsContent && it != shown }?.error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

        fun onAction(action: LibraryAction) {
            when (action) {
                is LibraryAction.SelectFilter -> savedStateHandle[FILTER_KEY] = action.filter
                is LibraryAction.SelectSort -> launch { preferencesRepository.setSort(filter.value, action.sort) }
                is LibraryAction.SetAlbumColumns -> launch { preferencesRepository.setAlbumColumns(action.columns) }
                is LibraryAction.SetFilterStyle -> launch { preferencesRepository.setFilterStyle(action.style) }
                LibraryAction.Refresh -> launch { syncRepository.sync() }
                LibraryAction.ErrorShown -> shownFailure.value = syncRepository.status.value as? SyncStatus.Failed
            }
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
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
    }

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

private val SyncStatus.lastSyncedAt
    get() =
        when (this) {
            is SyncStatus.Idle -> lastSyncedAt
            is SyncStatus.Failed -> lastSyncedAt
            is SyncStatus.Running -> null
        }

private const val FILTER_KEY = "filter"
