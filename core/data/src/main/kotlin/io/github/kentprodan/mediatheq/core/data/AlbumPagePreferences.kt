package io.github.kentprodan.mediatheq.core.data

/** What the album page's header draws behind the title (#132). */
enum class AlbumHeaderBackground { CoverImage, CoverColor, None }

/** The album page's sections, in their default order (#132). */
enum class AlbumSection { Tracks, MoreFromArtist, About }

/** The header buttons that can be hidden (#132); Play and More always show. A hidden one moves to More. */
enum class AlbumButton { Resume, Shuffle, PlayNext, AddToQueue, Favorite }

/** How "More from <artist>" sorts the artist's other albums (#132). */
enum class MoreAlbumsSort { YearDescending, YearAscending, Title }

/** Settings -> Interface -> Page settings -> Album page (#132). */
data class AlbumPagePreferences(
    val headerBackground: AlbumHeaderBackground = AlbumHeaderBackground.CoverImage,
    val showQuality: Boolean = true,
    val showRatingBar: Boolean = false,
    val hiddenButtons: Set<AlbumButton> = emptySet(),
    /** Every section once, in order. */
    val sectionOrder: List<AlbumSection> = AlbumSection.entries,
    val showMoreFromArtist: Boolean = true,
    val moreAlbumsSort: MoreAlbumsSort = MoreAlbumsSort.YearDescending,
    /** Off: a track's artists show only when they differ from the album artists. */
    val alwaysShowTrackArtists: Boolean = false,
    val showTrackNumbers: Boolean = true,
    val showTrackThumbnails: Boolean = false,
    val multilineTrackTitles: Boolean = false,
    val showAbout: Boolean = true,
)
