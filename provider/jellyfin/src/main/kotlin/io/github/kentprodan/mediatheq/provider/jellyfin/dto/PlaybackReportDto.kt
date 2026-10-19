package io.github.kentprodan.mediatheq.provider.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST /Sessions/Playing`, `/Playing/Progress` and `/Playing/Stopped` (Stopped ignores the extra fields). */
@Serializable
internal data class PlaybackReportDto(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean? = null,
    @SerialName("PlayMethod") val playMethod: String? = null,
    @SerialName("CanSeek") val canSeek: Boolean? = null,
    @SerialName("Failed") val failed: Boolean? = null,
)
