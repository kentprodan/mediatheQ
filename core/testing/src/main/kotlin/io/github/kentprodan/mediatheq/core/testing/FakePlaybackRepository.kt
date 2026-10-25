package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import java.util.concurrent.CopyOnWriteArrayList

/** [PlaybackRepository] that records what is asked and reported. */
class FakePlaybackRepository : PlaybackRepository {
    var failure: MqResult.Failure? = null

    /** How the server delivers the streams it gives. */
    var playMethod = PlayMethod.DirectPlay

    /** The file size the server reports for its streams. */
    var sizeBytes: Long? = null

    // Thread-safe: the prefetcher asks from an IO thread while tests read on the main thread.
    val streamRequests: MutableList<Pair<MediaId, DeviceProfile>> = CopyOnWriteArrayList()

    /** "start t1 @12s paused", "progress ...", "stopped ... failed" in call order. */
    val reports = mutableListOf<String>()

    override suspend fun stream(
        itemId: MediaId,
        profile: DeviceProfile,
    ) = failure
        ?: MqResult
            .Success(
                FakePlaybackSource.fakeStream(itemId).copy(playMethod = playMethod, sizeBytes = sizeBytes),
            ).also {
                streamRequests +=
                    itemId to profile
            }

    override suspend fun streamHeaders() = mapOf("Authorization" to "fake")

    override suspend fun reportStart(report: PlaybackReport) = record("start", report)

    override suspend fun reportProgress(report: PlaybackReport) = record("progress", report)

    override suspend fun reportStopped(
        report: PlaybackReport,
        failed: Boolean,
    ) = record("stopped", report, if (failed) " failed" else "")

    private fun record(
        kind: String,
        report: PlaybackReport,
        suffix: String = "",
    ): MqResult<Unit> {
        val paused = if (report.isPaused) " paused" else ""
        reports += "$kind ${report.stream.itemId.itemId} @${report.position.inWholeSeconds}s$paused$suffix"
        return failure ?: MqResult.Success(Unit)
    }
}
