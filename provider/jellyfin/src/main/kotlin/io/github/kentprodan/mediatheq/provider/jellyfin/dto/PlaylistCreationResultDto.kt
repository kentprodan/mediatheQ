package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Answer of `POST /Playlists`. */
@Serializable
internal data class PlaylistCreationResultDto(
    @SerialName("Id") val id: String? = null,
)
