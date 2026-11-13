package io.github.kentprodan.mediatheq.feature.tv.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.sortedByName
import io.github.kentprodan.mediatheq.core.data.sortedFor
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One list of the TV Library (#237): a tab ([filter]) or the albums of a [genre] or [year] (`null` year = unknown).
 * The same queries and sorts as the phone's Library; the sort is the one stored for [filter] on this device.
 */
@HiltViewModel(assistedFactory = TvLibraryViewModel.Factory::class)
class TvLibraryViewModel
    @AssistedInject
    constructor(
        @Assisted private val filter: LibraryFilter,
        @Assisted private val genre: MediaId?,
        @Assisted private val year: YearKey?,
        private val library: LibraryRepository,
        private val sync: LibrarySyncRepository,
        private val preferences: LibraryPreferencesRepository,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val sort = preferences.preferences.map { it.sortFor(filter) }.distinctUntilChanged()

        /** The sorted cards, with the A-Z strip when sorted by name. */
        private val items: Flow<TvLibraryContent.Items> =
            combine(sorter(), sort) { sorter, sort -> sorter(sort) }.flowOn(defaultDispatcher)

        /** The status, and whether a full sync ever completed (then an empty list is really empty). */
        private val status =
            sync.status.scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                status to (previous?.second == true || status.lastSyncedAt != null)
            }

        val uiState: StateFlow<TvLibraryUiState> =
            combine(items, sort, status) { items, sort, status ->
                val (current, everSynced) = status ?: return@combine TvLibraryUiState(filter, sort)
                val content =
                    when {
                        items.items.isNotEmpty() -> items
                        everSynced -> TvLibraryContent.Empty
                        current is SyncStatus.Failed -> TvLibraryContent.Error(current.error)
                        else -> TvLibraryContent.Loading
                    }
                TvLibraryUiState(filter, sort, content)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvLibraryUiState(filter))

        /** A genre page's title: the genre's name. */
        val genreName: StateFlow<String?> =
            (genre?.let { id -> library.genre(id).map { it?.name } } ?: flowOf(null))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun onAction(action: TvLibraryAction) {
            viewModelScope.launch {
                when (action) {
                    is TvLibraryAction.SetSort -> preferences.setSort(filter, action.sort)
                    TvLibraryAction.Sync -> sync.sync()
                }
            }
        }

        /** The list's items as a function of the sort. */
        private fun sorter(): Flow<(LibrarySort) -> TvLibraryContent.Items> =
            when (filter) {
                LibraryFilter.Artists -> {
                    library.albumArtists().map { all ->
                        { _ ->
                            val artists = all.sortedByName()
                            items(artists.map { it.sortName }, true) {
                                artists.map {
                                    TvLibraryItem(
                                        it.id,
                                        TvLibraryItem.Kind.Artist,
                                        it.name,
                                        artwork = it.artwork,
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryFilter.Playlists -> {
                    library.playlists().map { all ->
                        { sort ->
                            val playlists = all.sortedFor(sort)
                            items(playlists.map { it.sortName }, sort == LibrarySort.Name) {
                                playlists.map {
                                    TvLibraryItem(it.id, TvLibraryItem.Kind.Playlist, it.name, artwork = it.artwork)
                                }
                            }
                        }
                    }
                }

                LibraryFilter.Genres -> {
                    library.genres().map { all ->
                        { sort ->
                            val genres = all.sortedFor(sort)
                            items(genres.map { it.sortName }, sort == LibrarySort.Name) {
                                genres.map {
                                    TvLibraryItem(
                                        it.id,
                                        TvLibraryItem.Kind.Genre,
                                        it.name,
                                        artwork = it.artwork,
                                        albumCount = it.albumCount,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    val albums =
                        when {
                            genre != null -> library.genreAlbums(genre)
                            year != null -> library.albums().map { all -> all.filter { it.year == year.year } }
                            else -> library.albums()
                        }
                    albums.map { all ->
                        { sort ->
                            val sorted = all.sortedFor(sort)
                            items(sorted.map { it.sortName }, sort == LibrarySort.Name) {
                                sorted.map { album ->
                                    TvLibraryItem(
                                        album.id,
                                        TvLibraryItem.Kind.Album,
                                        album.title,
                                        album.albumArtists.joinToString { it.name }.ifEmpty { null },
                                        album.artwork,
                                    )
                                }
                            }
                        }
                    }
                }
            }

        private fun items(
            names: List<String>,
            byName: Boolean,
            cards: () -> List<TvLibraryItem>,
        ) = TvLibraryContent.Items(cards(), if (byName) alphabetIndex(names) else emptyList())

        @AssistedFactory
        interface Factory {
            fun create(
                filter: LibraryFilter,
                genre: MediaId?,
                year: YearKey?,
            ): TvLibraryViewModel
        }
    }
