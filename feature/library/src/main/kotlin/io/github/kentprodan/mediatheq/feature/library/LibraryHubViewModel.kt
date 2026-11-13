package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Library hub (#129): its tiles follow Settings -> Library page at once. The Movies and Series tiles (#270) show
 * their counts and hide when there is nothing to list (no video on the server, none synced, or offline).
 */
@HiltViewModel
class LibraryHubViewModel
    @Inject
    constructor(
        preferences: LibraryPreferencesRepository,
        videos: VideoRepository,
    ) : ViewModel() {
        private val counts =
            if (videos.hasVideo) {
                combine(videos.movieCount, videos.seriesCount) { movies, series ->
                    mapOf(LibraryShortcut.Movies to movies, LibraryShortcut.Series to series)
                }
            } else {
                flowOf(emptyMap())
            }

        val uiState: StateFlow<LibraryHubUiState> =
            combine(preferences.preferences.map { it.libraryPage }.distinctUntilChanged(), counts) { page, counts ->
                val shown = page.shortcuts.filter { it !in VIDEO_TILES || (counts[it] ?: 0) > 0 }
                // Never an empty hub: a list of video tiles only, all empty, falls back to every music tile.
                val tiles = shown.ifEmpty { LibraryShortcut.entries - VIDEO_TILES }
                LibraryHubUiState.Content(page.copy(shortcuts = tiles), counts)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryHubUiState.Loading)
    }

private val VIDEO_TILES = setOf(LibraryShortcut.Movies, LibraryShortcut.Series)
