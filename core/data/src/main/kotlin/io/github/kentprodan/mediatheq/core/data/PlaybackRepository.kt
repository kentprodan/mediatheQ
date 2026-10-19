package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream

/**
 * Streams and play-state reports for the signed-in server. Signed out, every call fails with `Unauthorized`; a 401 from
 * the server signs out with "session expired".
 */
interface PlaybackRepository {
    suspend fun stream(
        itemId: MediaId,
        profile: DeviceProfile,
    ): MqResult<PlaybackStream>

    /** Headers for stream requests, built from the current token; empty when signed out. */
    suspend fun streamHeaders(): Map<String, String>

    suspend fun reportStart(report: PlaybackReport): MqResult<Unit>

    suspend fun reportProgress(report: PlaybackReport): MqResult<Unit>

    suspend fun reportStopped(
        report: PlaybackReport,
        failed: Boolean,
    ): MqResult<Unit>
}
