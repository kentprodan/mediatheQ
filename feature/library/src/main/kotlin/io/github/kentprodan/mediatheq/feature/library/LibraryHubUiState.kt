package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut

/** The Library hub (#129). */
sealed interface LibraryHubUiState {
    /** The saved tiles are being read (no default tiles flash first). */
    data object Loading : LibraryHubUiState

    /** The chosen tiles and how they look (Settings -> Library page). */
    data class Content(
        val page: LibraryPagePreferences,
        /** How many movies and series there are (#270), shown on their tiles. */
        val counts: Map<LibraryShortcut, Int> = emptyMap(),
    ) : LibraryHubUiState
}

sealed interface LibraryHubAction {
    data class Open(
        val shortcut: LibraryShortcut,
    ) : LibraryHubAction
}
