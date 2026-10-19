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
