package io.github.kentprodan.mediatheq.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
    ) : ViewModel() {
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
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

        fun onAction(action: SearchAction) {
            when (action) {
                is SearchAction.QueryChanged -> {
                    query.value = action.query
                }

                SearchAction.Submit, SearchAction.ResultOpened -> {
                    saveQuery()
                }

                is SearchAction.PlayTrack -> {
                    playback.play(listOf(action.track))
                    saveQuery()
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
            when (val result = libraryRepository.search(query)) {
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
        }
    }
