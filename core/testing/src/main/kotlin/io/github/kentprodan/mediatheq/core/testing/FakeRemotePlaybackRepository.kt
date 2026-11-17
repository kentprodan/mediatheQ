package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.RemotePlaybackRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RemoteSession
import kotlin.time.Duration

/** [RemotePlaybackRepository] in memory: [devices] answers [sessions]; [sent] records each play. */
class FakeRemotePlaybackRepository(
    var sessions: List<RemoteSession> = emptyList(),
) : RemotePlaybackRepository {
    val sent = mutableListOf<Triple<RemoteSession, MediaId, Duration>>()
    var result: MqResult<Unit> = MqResult.Success(Unit)

    override suspend fun devices() = sessions

    override suspend fun playOn(
        session: RemoteSession,
        itemId: MediaId,
        startPosition: Duration,
    ): MqResult<Unit> {
        sent += Triple(session, itemId, startPosition)
        return result
    }
}
