package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
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
