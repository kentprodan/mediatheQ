package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RemoteSession
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import javax.inject.Inject
import kotlin.time.Duration

/** Sends a movie to another device running mediatheQ (#389: the phone to the TV). */
interface RemotePlaybackRepository {
    /** The other mediatheQ devices that play video now; none offline or on an error. */
    suspend fun devices(): List<RemoteSession>

    suspend fun playOn(
        session: RemoteSession,
        itemId: MediaId,
        startPosition: Duration,
    ): MqResult<Unit>
}

class DefaultRemotePlaybackRepository
    @Inject
    constructor(
        private val source: PlaybackSource,
        private val accountStore: AccountStore,
    ) : RemotePlaybackRepository {
        override suspend fun devices(): List<RemoteSession> {
            val server = accountStore.signedInServer() ?: return emptyList()
            return (source.remoteSessions(server) as? MqResult.Success)?.value.orEmpty()
        }

        override suspend fun playOn(
            session: RemoteSession,
            itemId: MediaId,
            startPosition: Duration,
        ): MqResult<Unit> {
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            return source.playOn(server, session, itemId, startPosition)
        }
    }
