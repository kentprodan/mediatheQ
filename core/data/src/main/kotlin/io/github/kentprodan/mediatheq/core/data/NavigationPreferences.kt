package io.github.kentprodan.mediatheq.core.data

/** A destination the main navigation can show (#122). Genres and Years join once their pages exist. */
enum class MainNavEntry { Home, Library, Albums, AlbumArtists, Genres, Playlists, Tracks, Search, Downloads }

/** How the main navigation looks in portrait. With a FAB, the last entry becomes the FAB. */
enum class PortraitNavStyle { BottomBar, FloatingToolbar, FloatingToolbarWithFab }

/** How the main navigation looks in landscape. */
enum class LandscapeNavStyle { NavigationRail, SameAsPortrait }

/** What tapping a track in a list does. */
enum class TrackAction {
    /** The list becomes the queue, starting at the tapped track. */
    Play,
    PlayOnlyThis,
    PlayNext,
    AddToQueue,
}

/** Settings -> Interface -> Navigation and media start (#122). */
data class NavigationPreferences(
    /** Always valid: see [validNavEntries]. */
    val entries: List<MainNavEntry> = DEFAULT_ENTRIES,
    val portraitStyle: PortraitNavStyle = PortraitNavStyle.BottomBar,
    val landscapeStyle: LandscapeNavStyle = LandscapeNavStyle.NavigationRail,
    /** Of the navigation container's background, 0-100 in steps of [TRANSPARENCY_STEP]. */
    val transparencyPercent: Int = 0,
    val slowStartMessages: Boolean = true,
    val trackAction: TrackAction = TrackAction.Play,
    val expandPlayer: Boolean = false,
) {
    companion object {
        const val MAX_ENTRIES = 6
        const val TRANSPARENCY_STEP = 10
        val DEFAULT_ENTRIES =
            listOf(MainNavEntry.Home, MainNavEntry.Library, MainNavEntry.Search, MainNavEntry.Downloads)
    }
}

/**
 * [entries] without repeats, with [MainNavEntry.Home] (added first when missing: Settings is reached from it) and at
 * most [NavigationPreferences.MAX_ENTRIES], dropping the extra ones at the end.
 */
fun validNavEntries(entries: List<MainNavEntry>): List<MainNavEntry> {
    val distinct = entries.distinct()
    val withHome = if (MainNavEntry.Home in distinct) distinct else listOf(MainNavEntry.Home) + distinct
    var others = 0
    return withHome.filter { it == MainNavEntry.Home || others++ < NavigationPreferences.MAX_ENTRIES - 1 }
}

/** [percent] clamped to 0-100 and rounded to the nearest step. */
fun validTransparency(percent: Int): Int {
    val step = NavigationPreferences.TRANSPARENCY_STEP
    return (percent.coerceIn(0, 100) + step / 2) / step * step
}
