package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Playlists`. Defaults are not encoded, so [mediaType] has none. */
@Serializable
internal data class CreatePlaylistDto(
    @SerialName("Name") val name: String,
    @SerialName("UserId") val userId: String,
    @SerialName("MediaType") val mediaType: String,
)
