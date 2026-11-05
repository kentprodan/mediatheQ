package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.serialization.Serializable

/** An album's page (#11). */
@Serializable
data class AlbumKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
) : NavKey

/** An artist's page (#15). */
@Serializable
data class ArtistKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
) : NavKey

/** Every played track of an artist, most played first: the artist page's "Show all" (#133). */
@Serializable
data class ArtistTopTracksKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
) : NavKey

/** A genre's page (#130). */
@Serializable
data class GenreKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
) : NavKey

/** A record label's page (#131): its albums; [name] titles the page. */
@Serializable
data class RecordLabelKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
    val name: String,
) : NavKey

/** A playlist's page (#16). */
@Serializable
data class PlaylistKey(
    @Serializable(with = MediaIdSerializer::class) val id: MediaId,
) : NavKey

/**
 * The "Add to playlist" dialog (#16) for [trackIds], in order. With no tracks it only creates an empty playlist.
 * shortcut: the ids live in the saved back stack (~70 bytes each); pass an album id instead if lists of thousands appear.
 */
@Serializable
data class AddToPlaylistKey(
    val trackIds: List<
        @Serializable(with = MediaIdSerializer::class)
        MediaId,
    >,
) : NavKey

/** A Library list opened from the hub (#129), on top of the Library tab: it has a Back button. */
@Serializable
data class LibraryListKey(
    val shortcut: LibraryShortcut,
) : NavKey

/** The albums of a year (#129); [year] `null` = albums without a year. */
@Serializable
data class YearKey(
    val year: Int?,
) : NavKey
