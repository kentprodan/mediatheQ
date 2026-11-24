package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle

/** A destination the main navigation can show (#122). */
enum class MainNavEntry {
    Home,
    Library,
    Albums,
    AlbumArtists,
    Genres,
    RecordLabels,
    Playlists,
    Tracks,
    Search,
    Downloads,

    /** The video grids as tabs of their own (#325), off by default. */
    Movies,
    Series,
    ;

    /**
     * Shown in [mode] (ADR 0051): the music lists only with music, Movies and Series only with video; Home, Library,
     * Search and Downloads always.
     */
    fun allowed(mode: AppMode): Boolean =
        when (this) {
            Home, Library, Search, Downloads -> true
            Albums, AlbumArtists, Genres, RecordLabels, Playlists, Tracks -> mode.music
            Movies, Series -> mode.video
        }
}

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
    /** TV (#444): a plain fast fade between pages instead of the standard fade and slight scale. */
    val simpleTransitions: Boolean = false,
    /** TV (#521): the featured carousel at the top of Home. */
    val tvCarousel: TvCarousel = TvCarousel.Card,
    /**
     * TV (#575): the Home sections the carousel mixes, in order; `null` = the default (new movies, new episodes and
     * Continue watching with video, else new albums).
     */
    val tvCarouselSources: List<HomeSection>? = null,
    /** TV (#521): the carousel leaves out watched movies and episodes. */
    val tvCarouselSkipWatched: Boolean = true,
    /** TV (#521): Home rows shown as immersive lists (the focused card's backdrop and details behind the row). */
    val tvImmersiveRows: Set<HomeSection> = emptySet(),
    /** TV (#520): how the side menu opens. */
    val tvMenuStyle: TvMenuStyle = TvMenuStyle.Modal,
    /** TV (#523): the title of the detail pages; the logo by default. */
    val tvTitle: TvTitleStyle = TvTitleStyle.Logo,
    /** TV (#578): the logo title's size. */
    val tvLogoSize: TvLogoSize = TvLogoSize.Medium,
    /** TV (#517): detail pages take their accent from the poster or cover; off keeps Iris. */
    val tvPosterAccent: Boolean = false,
    /** TV (#518): the card variant a Home row uses; a row missing here keeps its own default. */
    val tvCardStyles: Map<HomeSection, TvCardStyle> = emptyMap(),
    /** TV (#604): how many cards of a Home row fit on screen ([CARDS_PER_SCREEN]); a row missing here keeps its default. */
    val tvCardsPerScreen: Map<HomeSection, Int> = emptyMap(),
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

/**
 * The entries the navigation shows in [mode] (ADR 0051): the chosen ones, or the defaults when [mode] drops any of them
 * (the choice stays stored, so switching back brings it back).
 */
fun NavigationPreferences.entriesFor(mode: AppMode): List<MainNavEntry> =
    if (entries.all { it.allowed(mode) }) entries else NavigationPreferences.DEFAULT_ENTRIES

/** [percent] clamped to 0-100 and rounded to the nearest step. */
fun validTransparency(percent: Int): Int {
    val step = NavigationPreferences.TRANSPARENCY_STEP
    return (percent.coerceIn(0, 100) + step / 2) / step * step
}

/** The guideline's TV card variants (#518), chosen per Home row. */
enum class TvCardStyle { Standard, Classic, Compact, WideStandard, WideClassic }

/** The guideline's card layouts (#604): one to five cards across the screen. */
val CARDS_PER_SCREEN = 1..5

/** TV Home's featured carousel (#521, Google TV guidelines): off, an inset card, or immersive (full width). */
enum class TvCarousel { Off, Card, Immersive }

/**
 * The TV side menu's style (#520, Google TV guidelines): Modal floats over the dimmed screen (the default), Standard
 * pushes the screen aside while open.
 */
enum class TvMenuStyle { Modal, Standard }

/** The TV carousel's sections when none are chosen (#575, owner): new movies, new episodes, Continue watching. */
val DefaultTvCarouselSources: List<HomeSection> =
    listOf(HomeSection.RecentlyAddedMovies, HomeSection.RecentlyAddedEpisodes, HomeSection.ContinueWatching)
