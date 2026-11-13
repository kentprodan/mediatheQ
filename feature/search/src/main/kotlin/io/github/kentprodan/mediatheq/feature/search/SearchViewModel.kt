package io.github.kentprodan.mediatheq.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.downloadAlbums
import io.github.kentprodan.mediatheq.core.data.downloadPlaylists
import io.github.kentprodan.mediatheq.core.data.tracksOfAlbums
import io.github.kentprodan.mediatheq.core.data.tracksOfArtists
import io.github.kentprodan.mediatheq.core.data.tracksOfPlaylists
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val preferencesRepository: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        private val playlists: PlaylistRepository,
        appMode: AppModeRepository,
    ) : ViewModel() {
        private val selection = MutableStateFlow(ListSelection<MediaId>())
        private val addToPlaylist = MutableStateFlow<List<MediaId>?>(null)

        // Kept apart from the search: a favorite or rating reads the results again and must not reset the chip; it
        // also stays for the next query (All shows while it has nothing).
        private val filter = MutableStateFlow(SearchFilter.All)
        private val page = preferencesRepository.preferences.map { it.searchPage }.distinctUntilChanged()

        // The search field restores its own text after process death and sends it again, so this is not saved.
        private val query = MutableStateFlow("")
        private val retries = MutableStateFlow(0)

        // shortcut: a query runs once, so a sync finishing (or, in offline mode, a download completing) while results
        // show does not refresh them; retype to see it.
        val uiState: StateFlow<SearchUiState> =
            // Offline mode on or off runs the query again: it searches other items (#63).
            combine(query, retries, libraryRepository.offline) { query, _, offline -> query.trim() to offline }
                // Clearing the field shows the recent searches at once; typing waits for a short pause.
                .debounce { (query, _) -> if (query.isEmpty()) 0L else DEBOUNCE_MILLIS }
                .flatMapLatest<Pair<String, Boolean>, SearchUiState> { (query, offline) ->
                    if (query.isEmpty()) {
                        preferencesRepository.recentSearches.map { SearchUiState.Recent(it) }
                    } else {
                        flow { emit(search(query, offline)) }
                    }
                }.combine(combine(selection, addToPlaylist, filter, page, ::Extras)) { state, extras ->
                    if (state is SearchUiState.Results) {
                        val shown =
                            state.copy(
                                addToPlaylist = extras.addToPlaylist,
                                chosenFilter = extras.filter,
                                page = extras.page,
                            )
                        // Results can change (a favorite read again) while selected, and a chip hides the others.
                        shown.copy(selection = extras.selection.retain(shown.ids))
                    } else {
                        state
                    }
                }.combine(appMode.mode) { state, mode -> if (mode.music) state else SearchUiState.VideoMode }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

        fun onAction(action: SearchAction) {
            when (action) {
                is SearchAction.QueryChanged -> {
                    if (action.query != query.value) selection.value = ListSelection()
                    query.value = action.query
                }

                SearchAction.Submit, SearchAction.ResultOpened -> {
                    saveQuery()
                }

                is SearchAction.PlayTrack -> {
                    // The shown songs are the list it was tapped in: Play makes them the queue (#136).
                    // By id: a favorite read again changes the track's user data, not which song it is.
                    val state = uiState.value as? SearchUiState.Results
                    val shown = state?.shown?.tracks.orEmpty()
                    val index = shown.indexOfFirst { it.id == action.track.id }
                    val tracks = if (index >= 0) shown else listOf(action.track)
                    viewModelScope.launch {
                        trackTaps.tap(tracks, index.coerceAtLeast(0), action = state?.page?.trackAction)
                    }
                    saveQuery()
                }

                is SearchAction.SelectFilter -> {
                    filter.value = action.filter
                }

                is SearchAction.Download -> {
                    downloads.download(listOf(action.track))
                }

                is SearchAction.PlayNext -> {
                    playback.playNext(listOf(action.track))
                }

                is SearchAction.AddToQueue -> {
                    playback.addToQueue(listOf(action.track))
                }

                is SearchAction.SwipeTrack -> {
                    viewModelScope.launch {
                        // Results are read once: only a favorite change needs them read again.
                        if (commands.swipe(action.track, action.right) == SwipeAction.ToggleFavorite) retries.value++
                    }
                }

                is SearchAction.RateTrack -> {
                    viewModelScope.launch {
                        commands.rate(action.track, action.halfStars)
                        retries.value++
                    }
                }

                is SearchAction.ToggleSelection -> {
                    selection.update { it.toggle(action.id) }
                }

                SearchAction.SelectAll -> {
                    (uiState.value as? SearchUiState.Results)?.let {
                        selection.value =
                            ListSelection<MediaId>().selectAll(it.ids)
                    }
                }

                SearchAction.ClearSelection -> {
                    selection.value = ListSelection()
                }

                is SearchAction.OnSelection -> {
                    val state = uiState.value as? SearchUiState.Results ?: return
                    val picked = state.selection
                    selection.value = ListSelection()
                    viewModelScope.launch {
                        val results = state.shown
                        val albumIds = picked.pick(results.albums) { it.id }.map { it.id }
                        val playlistIds = picked.pick(results.playlists) { it.id }.map { it.id }
                        val loose =
                            libraryRepository.tracksOfArtists(picked.pick(results.artists) { it.id }.map { it.id }) +
                                picked.pick(results.tracks) { it.id }
                        // Shown order: artists, albums, songs, playlists; an album and one of its songs both
                        // selected give that song once.
                        val tracks =
                            (
                                libraryRepository.tracksOfArtists(
                                    picked.pick(results.artists) { it.id }.map { it.id },
                                ) +
                                    libraryRepository.tracksOfAlbums(albumIds) +
                                    picked.pick(results.tracks) { it.id } +
                                    playlists.tracksOfPlaylists(playlistIds)
                            ).distinctBy { it.id }
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
                                downloads.downloadAlbums(libraryRepository, albumIds)
                                downloads.downloadPlaylists(playlists, playlistIds)
                                commands.download(loose)
                            }

                            SelectionCommand.AddToPlaylist -> {
                                addToPlaylist.value = tracks.map { it.id }
                            }

                            SelectionCommand.Favorite -> {
                                commands.toggleFavorite(tracks)
                                retries.value++
                            }
                        }
                    }
                }

                SearchAction.AddToPlaylistShown -> {
                    addToPlaylist.value = null
                }

                is SearchAction.SetFavorite -> {
                    viewModelScope.launch {
                        userData.setFavorite(action.track.id, action.isFavorite)
                        // Results are read once; read them again so the menu shows the new state.
                        retries.value++
                    }
                }

                SearchAction.ClearRecent -> {
                    viewModelScope.launch { preferencesRepository.clearRecentSearches() }
                }

                SearchAction.Retry -> {
                    retries.value++
                }
            }
        }

        private fun saveQuery() {
            val query = query.value
            viewModelScope.launch { preferencesRepository.addRecentSearch(query) }
        }

        private suspend fun search(
            query: String,
            offline: Boolean,
        ): SearchUiState =
            when (val result = libraryRepository.search(query, FULL_LIMIT)) {
                is MqResult.Failure -> {
                    SearchUiState.Error(result.error)
                }

                is MqResult.Success -> {
                    if (result.value.isEmpty()) {
                        SearchUiState.NoResults(query, offline)
                    } else {
                        SearchUiState.Results(result.value)
                    }
                }
            }

        internal companion object {
            /** Short: a local search takes milliseconds, this only skips the queries of fast typing. */
            const val DEBOUNCE_MILLIS = 50L

            /**
             * A chip's full list (#136). shortcut: capped at 500 per kind, and every favorite or rating reads them all
             * again (a few ms locally); page the chip lists if a library ever has more matches.
             */
            const val FULL_LIMIT = 500
        }
    }

private data class Extras(
    val selection: ListSelection<MediaId>,
    val addToPlaylist: List<MediaId>?,
    val filter: SearchFilter,
    val page: SearchPagePreferences,
)
