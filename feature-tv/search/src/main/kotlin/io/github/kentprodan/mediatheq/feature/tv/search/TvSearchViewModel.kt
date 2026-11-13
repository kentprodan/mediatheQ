package io.github.kentprodan.mediatheq.feature.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search on TV (#244): the same diacritic-insensitive search as the phone (`LibraryRepository.search`, the FTS of
 * #17), run as the user types after a short pause; the recent searches while the field is empty.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TvSearchViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val preferences: LibraryPreferencesRepository,
        private val trackTaps: TrackTaps,
    ) : ViewModel() {
        private val text = MutableStateFlow("")

        /** Bumped by the Search key after a failed search, so the same query runs again. */
        private val retries = MutableStateFlow(0)

        /** What the field shows. */
        val query: StateFlow<String> = text.asStateFlow()

        val uiState: StateFlow<TvSearchUiState> =
            combine(text.map { it.trim() }.distinctUntilChanged(), retries) { query, _ -> query }
                // Clearing the field shows the recent searches at once; typing waits for a short pause.
                .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }
                .flatMapLatest<String, TvSearchUiState> { query ->
                    if (query.isEmpty()) {
                        preferences.recentSearches.map { TvSearchUiState.Recent(it) }
                    } else {
                        flow { emit(search(query)) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSearchUiState.Loading)

        fun onAction(action: TvSearchAction) {
            when (action) {
                is TvSearchAction.QueryChanged -> {
                    text.value = action.query
                }

                TvSearchAction.Submit -> {
                    if (uiState.value is TvSearchUiState.Error) retries.value++
                    saveQuery()
                }

                TvSearchAction.Opened -> {
                    saveQuery()
                }

                is TvSearchAction.PickRecent -> {
                    text.value = action.query
                    saveQuery()
                }

                is TvSearchAction.PlayTrack -> {
                    val tracks = (uiState.value as? TvSearchUiState.Results)?.tracks ?: return
                    viewModelScope.launch { trackTaps.tap(tracks, action.index) }
                    saveQuery()
                }
            }
        }

        private fun saveQuery() {
            val query = text.value.trim()
            if (query.isNotEmpty()) viewModelScope.launch { preferences.addRecentSearch(query) }
        }

        private suspend fun search(query: String): TvSearchUiState =
            when (val result = library.search(query)) {
                is MqResult.Failure -> {
                    TvSearchUiState.Error(result.error)
                }

                is MqResult.Success -> {
                    val found = result.value
                    TvSearchUiState
                        .Results(found.albums, found.artists, found.tracks, found.playlists)
                        .takeUnless {
                            it.albums.isEmpty() && it.artists.isEmpty() && it.tracks.isEmpty() && it.playlists.isEmpty()
                        } ?: TvSearchUiState.NoResults
                }
            }

        private companion object {
            /** As on the phone: the local FTS answers in milliseconds, so results follow the typing. */
            const val DEBOUNCE_MILLIS = 50L
        }
    }
