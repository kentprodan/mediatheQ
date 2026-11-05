package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.LibraryShortcut

/** How the Library hub shows its tiles: a grid, or one row per tile. */
enum class LibraryHubStyle { Grid, List }

/** The gap between the hub's tiles (spacing tokens s / m / l). */
enum class LibraryHubSpacing { Compact, Normal, Large }

/** Settings -> Interface -> Page settings -> Library page (#129). */
data class LibraryPagePreferences(
    /** The hub's tiles in order; never empty. */
    val shortcuts: List<LibraryShortcut> = LibraryShortcut.entries,
    val style: LibraryHubStyle = LibraryHubStyle.Grid,
    val columns: Int = DEFAULT_COLUMNS,
    val landscapeColumns: Int = DEFAULT_LANDSCAPE_COLUMNS,
    val spacing: LibraryHubSpacing = LibraryHubSpacing.Normal,
) {
    companion object {
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_LANDSCAPE_COLUMNS = 5
        val COLUMNS = 2..4
        val LANDSCAPE_COLUMNS = 3..6
    }
}
