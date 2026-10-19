package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer

/** [PlaybackSource] that answers with a direct stream per item; [failure] makes every call fail. */
class FakePlaybackSource : PlaybackSource {
    var failure: MqResult.Failure? = null
    val reports = mutableListOf<String>()
    val profiles = mutableListOf<DeviceProfile>()

    override suspend fun stream(
        server: SignedInServer,
        itemId: MediaId,
        profile: DeviceProfile,
    ): MqResult<PlaybackStream> {
        profiles += profile
        return failure ?: MqResult.Success(fakeStream(itemId))
    }

    override fun streamHeaders(server: SignedInServer) =
        mapOf("Authorization" to "token=${server.connection.accessToken}")

    override suspend fun reportStart(
        server: SignedInServer,
        report: PlaybackReport,
    ) = record("start", report)

    override suspend fun reportProgress(
        server: SignedInServer,
        report: PlaybackReport,
    ) = record("progress", report)

    override suspend fun reportStopped(
        server: SignedInServer,
        report: PlaybackReport,
        failed: Boolean,
    ) = record("stopped", report)

    private fun record(
        kind: String,
        report: PlaybackReport,
    ): MqResult<Unit> {
        reports += "$kind ${report.stream.itemId.itemId}"
        return failure ?: MqResult.Success(Unit)
    }

    companion object {
        fun fakeStream(itemId: MediaId) =
            PlaybackStream(
                itemId,
                "http://nas/${itemId.itemId}",
                PlayMethod.DirectPlay,
                "src-${itemId.itemId}",
                "session-${itemId.itemId}",
                true,
            )
    }
}
