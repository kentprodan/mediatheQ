package io.github.kentprodan.mediatheq.feature.library

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val syncRepository: LibrarySyncRepository,
        private val preferencesRepository: LibraryPreferencesRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val filter = MutableStateFlow(LibraryFilter.Albums)

        // The failed sync the user already saw as a snackbar; the same failure is not shown twice.
        private val shownFailure = MutableStateFlow<SyncStatus.Failed?>(null)

        /** The current tab's items, sorted off the main thread; only the open tab's items are loaded. */
        private val items: Flow<LibraryContent> =
            filter
                .flatMapLatest { filter ->
                    val sort = preferencesRepository.preferences.map { it.sortFor(filter) }.distinctUntilChanged()
                    itemsOf(filter).combine(sort) { items, sort -> items(sort) }
                }.flowOn(defaultDispatcher)

        val uiState: StateFlow<LibraryUiState> =
            combine(filter, preferencesRepository.preferences, items, syncRepository.status, shownFailure) {
                filter,
                preferences,
                items,
                status,
                shown,
                ->
                val hasItems = items.isNotEmptyContent()
                LibraryUiState(
                    filter = filter,
                    filterStyle = preferences.filterStyle,
                    albumColumns = preferences.albumColumns,
                    sort = preferences.sortFor(filter),
                    content = if (hasItems) items else emptyContent(status),
                    refreshing = status is SyncStatus.Running,
                    refreshError = (status as? SyncStatus.Failed)?.takeIf { hasItems && it != shown }?.error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

        fun onAction(action: LibraryAction) {
            when (action) {
                is LibraryAction.SelectFilter -> filter.value = action.filter
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

/** What an empty tab shows, depending on the sync (see the table in the issue #10 plan). */
private fun emptyContent(status: SyncStatus): LibraryContent =
    when (status) {
        is SyncStatus.Running -> {
            LibraryContent.Loading
        }

        is SyncStatus.Idle -> {
            if (status.lastSyncedAt == null) LibraryContent.Loading else LibraryContent.Empty
        }

        is SyncStatus.Failed -> {
            if (status.error ==
                MqError.Network
            ) {
                LibraryContent.Offline
            } else {
                LibraryContent.Error(status.error)
            }
        }
    }
