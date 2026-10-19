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

        override suspend fun reportStart(report: PlaybackReport) =
            withReportServer { playbackSource.reportStart(it, report) }

        override suspend fun reportProgress(report: PlaybackReport) =
            withReportServer { playbackSource.reportProgress(it, report) }

        override suspend fun reportStopped(
            report: PlaybackReport,
            failed: Boolean,
        ) = withReportServer { playbackSource.reportStopped(it, report, failed) }

        // The server of the last stream: right after sign-out the final Stopped still goes out (best effort), so the
        // server can end a running transcode.
        @Volatile private var lastServer: SignedInServer? = null

        private suspend fun <T> withServer(call: suspend (SignedInServer) -> MqResult<T>): MqResult<T> {
            // Signed out meanwhile: nothing to do, and not a session expiry.
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            lastServer = server
            return expireOn401(call(server))
        }

        private suspend fun <T> withReportServer(call: suspend (SignedInServer) -> MqResult<T>): MqResult<T> {
            val current = accountStore.signedInServer()
            val server = current ?: lastServer ?: return MqResult.Failure(MqError.Unauthorized)
            val result = call(server)
            // A 401 after a deliberate sign-out is expected (the token was revoked), not a session expiry.
            return if (current != null) expireOn401(result) else result
        }

        private suspend fun <T> expireOn401(result: MqResult<T>): MqResult<T> {
            if ((result as? MqResult.Failure)?.error == MqError.Unauthorized) {
                withContext(NonCancellable) { sessionRepository.onUnauthorized() }
            }
            return result
        }
    }
