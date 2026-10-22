package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Playlists/{id}`; fields left out keep their value on the server. */
@Serializable
internal data class UpdatePlaylistDto(
    @SerialName("Name") val name: String,
)
