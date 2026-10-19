package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.server.PlaybackSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultPlaybackRepository
    @Inject
    constructor(
        private val playbackSource: PlaybackSource,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
    ) : PlaybackRepository {
        override suspend fun stream(
            itemId: MediaId,
            profile: DeviceProfile,
        ): MqResult<PlaybackStream> = withServer { playbackSource.stream(it, itemId, profile) }

        override suspend fun streamHeaders(): Map<String, String> =
            accountStore.signedInServer()?.let { playbackSource.streamHeaders(it) }.orEmpty()

        override suspend fun reportStart(report: PlaybackReport) = withServer { playbackSource.reportStart(it, report) }

        override suspend fun reportProgress(report: PlaybackReport) =
            withServer { playbackSource.reportProgress(it, report) }

        override suspend fun reportStopped(
            report: PlaybackReport,
            failed: Boolean,
        ) = withServer { playbackSource.reportStopped(it, report, failed) }

        private suspend fun <T> withServer(call: suspend (SignedInServer) -> MqResult<T>): MqResult<T> {
            // Signed out meanwhile: nothing to do, and not a session expiry.
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            val result = call(server)
            if ((result as? MqResult.Failure)?.error == MqError.Unauthorized) {
                withContext(NonCancellable) { sessionRepository.onUnauthorized() }
            }
            return result
        }
    }
