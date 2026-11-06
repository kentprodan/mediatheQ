package io.github.kentprodan.mediatheq.core.data

/** What the playlist page's header draws behind the name (#135). */
enum class PlaylistHeaderBackground {
    /** The playlist's image (the server's collage) edge to edge. */
    Image,

    /** A gradient from the image's color behind a smaller image. */
    ImageColor,
    None,
}

/** The header buttons that can be hidden (#135); Play and More always show. A hidden one moves to More. */
enum class PlaylistButton { Resume, Shuffle, PlayNext, AddToQueue, Favorite }

/** Settings -> Interface -> Page settings -> Playlist page (#135). */
data class PlaylistPagePreferences(
    val headerBackground: PlaylistHeaderBackground = PlaylistHeaderBackground.Image,
    val hiddenButtons: Set<PlaylistButton> = emptySet(),
    /** The page takes its colors from the playlist's image (ADR 0029); off by default here. */
    val dynamicColors: Boolean = false,
)
