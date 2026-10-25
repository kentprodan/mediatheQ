package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /Audio/{itemId}/Lyrics`: the server's parsed lyrics file. `Metadata.IsSynced` is not read (R4). */
@Serializable
internal data class LyricDto(
    @SerialName("Lyrics") val lyrics: List<LyricLineDto> = emptyList(),
)

@Serializable
internal data class LyricLineDto(
    @SerialName("Text") val text: String = "",
    /** In ticks (100 ns), `null` for plain lyrics. */
    @SerialName("Start") val start: Long? = null,
)
