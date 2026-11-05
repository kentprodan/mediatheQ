package io.github.kentprodan.mediatheq.core.data

/** What the genre page's header draws behind the name (#134). */
enum class GenreHeaderBackground {
    /** The header image (the most played album's cover) edge to edge. */
    Image,

    /** A gradient from the image's color. */
    ImageColor,
    None,
}

/** The genre page's sections, in their default order (#134). */
enum class GenreSection { Artists, AlbumArtists, Albums, TopTracks }

/** The header buttons that can be hidden (#134); Play and More always show. A hidden one moves to More. */
enum class GenreButton { Shuffle, PlayNext, AddToQueue }

/** Settings -> Interface -> Page settings -> Genre page (#134). */
data class GenrePagePreferences(
    val headerBackground: GenreHeaderBackground = GenreHeaderBackground.Image,
    val hiddenButtons: Set<GenreButton> = emptySet(),
    /** Every section once, in order. */
    val sectionOrder: List<GenreSection> = GenreSection.entries,
    val showAlbums: Boolean = true,
    val showArtists: Boolean = true,
    val showAlbumArtists: Boolean = false,
    val showTopTracks: Boolean = true,
    val topTracksCount: Int = DEFAULT_TOP_TRACKS,
    /** The page takes its colors from the header image (ADR 0029); off by default here. */
    val dynamicColors: Boolean = false,
) {
    companion object {
        const val DEFAULT_TOP_TRACKS = 15
        val TOP_TRACKS = 5..50
    }
}
