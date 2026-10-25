package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `PlaybackInfo`: where the media is and how the server will deliver it. */
@Serializable
internal data class PlaybackInfoResponseDto(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    /** `NotAllowed`, `NoCompatibleStream` or `RateLimitExceeded`. */
    @SerialName("ErrorCode") val errorCode: String? = null,
)

@Serializable
internal data class MediaSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    /** `http` (one live stream) or `hls` (a playlist of segments). */
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("Size") val size: Long? = null,
) {
    override fun toString(): String = "MediaSourceDto(id=$id, supportsDirectPlay=$supportsDirectPlay)"
}
