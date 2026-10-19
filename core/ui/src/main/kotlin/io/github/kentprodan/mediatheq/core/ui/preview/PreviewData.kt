package io.github.kentprodan.mediatheq.core.ui.preview

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Deterministic domain fixtures for previews and screenshot tests. */
object PreviewData {
    private val server = ServerId("preview")

    val artist = Artist(MediaId(server, "artist-1"), "Ada Moșoi")

    val track =
        Track(
            id = MediaId(server, "track-1"),
            title = "Morning Light",
            artists = listOf(artist),
            duration = 3.minutes + 45.seconds,
            trackNumber = 3,
        )

    val longTrack =
        track.copy(
            id = MediaId(server, "track-2"),
            title = "An Extremely Long Track Title That Will Never Fit On One Line Of A Phone",
            artists = listOf(artist, Artist(MediaId(server, "artist-2"), "The Orchestra of Very Long Names")),
            duration = 1.minutes * 72 + 5.seconds,
            trackNumber = 12,
        )

    val album =
        Album(
            id = MediaId(server, "album-1"),
            title = "Northern Lights",
            albumArtists = listOf(artist),
            year = 2024,
            trackCount = 11,
        )

    val longAlbum =
        album.copy(
            id = MediaId(server, "album-2"),
            title = "A Very Long Album Title That Must Ellipsize",
            albumArtists = longTrack.artists,
        )
}
