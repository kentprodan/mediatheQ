package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Items/{id}/PlaybackInfo`. */
@Serializable
internal data class PlaybackInfoDto(
    @SerialName("UserId") val userId: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int?,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto,
    // No defaults: our JSON omits default values, and these must always be sent.
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean,
    /** Where a conversion starts (100 ns ticks; ADR 0050). */
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)
