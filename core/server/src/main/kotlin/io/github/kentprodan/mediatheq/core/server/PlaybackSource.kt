package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream

/** Streams and play-state reporting (jellyfin skill, sections 5 and 6). */
interface PlaybackSource {
    /** Asks the server how to play [itemId] on a device with [profile]: as it is, or converted. */
    suspend fun stream(
        server: SignedInServer,
        itemId: MediaId,
        profile: DeviceProfile,
    ): MqResult<PlaybackStream>

    /** HTTP headers a stream request needs (authentication). Built fresh from [server]; never cache them. */
    fun streamHeaders(server: SignedInServer): Map<String, String>

    suspend fun reportStart(
        server: SignedInServer,
        report: PlaybackReport,
    ): MqResult<Unit>

    suspend fun reportProgress(
        server: SignedInServer,
        report: PlaybackReport,
    ): MqResult<Unit>

    /** Ends the play session; for a transcode this also stops the server-side conversion. */
    suspend fun reportStopped(
        server: SignedInServer,
        report: PlaybackReport,
        failed: Boolean,
    ): MqResult<Unit>
}
