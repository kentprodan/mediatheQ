package io.github.kentprodan.mediatheq.core.data

/** Which favorites download by themselves (#145). */
enum class AutoDownloads {
    Disabled,
    Tracks,
    TracksAndAlbums,
    TracksAlbumsAndPlaylists,
    ;

    val tracks: Boolean get() = this != Disabled
    val albums: Boolean get() = this == TracksAndAlbums || this == TracksAlbumsAndPlaylists
    val playlists: Boolean get() = this == TracksAlbumsAndPlaylists
}
