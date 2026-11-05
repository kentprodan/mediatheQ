package io.github.kentprodan.mediatheq.core.data

/** What the artist page's header draws behind the name (#133). */
enum class ArtistHeaderBackground {
    /** The fanart, or the artist image when there is none or it does not load. */
    Backdrop,
    ArtistImage,

    /** A gradient from the artist image's color. */
    ImageColor,
    None,
}

/** The artist page's sections; [ArtistPagePreferences.sectionOrder] holds the default order (#133). */
enum class ArtistSection { Albums, AppearsOn, TopTracks, About }

/** The header buttons that can be hidden (#133); Play and More always show. A hidden one moves to More. */
enum class ArtistButton { Shuffle, PlayNext, AddToQueue, Favorite }

/** What the header's Play button plays (#133). */
enum class ArtistPlayAction {
    /** The artist's own albums in the page's album order, then the tracks it is credited on. */
    Albums,
    TopTracks,

    /** Every track of the artist, shuffled. */
    AllShuffled,
}

/** Settings -> Interface -> Page settings -> Artist page (#133). */
data class ArtistPagePreferences(
    val headerBackground: ArtistHeaderBackground = ArtistHeaderBackground.Backdrop,
    val hiddenButtons: Set<ArtistButton> = emptySet(),
    val playAction: ArtistPlayAction = ArtistPlayAction.Albums,
    /** Every section once, in order. */
    val sectionOrder: List<ArtistSection> = DEFAULT_SECTIONS,
    val albumsSort: MoreAlbumsSort = MoreAlbumsSort.YearDescending,
    val albumColumns: Int = DEFAULT_COLUMNS,
    val landscapeAlbumColumns: Int = DEFAULT_LANDSCAPE_COLUMNS,
    val showAppearsOn: Boolean = true,
    val showTopTracks: Boolean = true,
    val topTracksCount: Int = DEFAULT_TOP_TRACKS,
    val showAbout: Boolean = true,
    /** The page takes its colors from the artist image (ADR 0029). */
    val dynamicColors: Boolean = true,
) {
    companion object {
        val DEFAULT_SECTIONS =
            listOf(ArtistSection.Albums, ArtistSection.TopTracks, ArtistSection.AppearsOn, ArtistSection.About)
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_LANDSCAPE_COLUMNS = 5
        const val DEFAULT_TOP_TRACKS = 15
        val COLUMNS = 2..4
        val LANDSCAPE_COLUMNS = 3..6
        val TOP_TRACKS = 5..50
    }
}
