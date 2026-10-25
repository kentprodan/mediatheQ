package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LyricsSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultLyricsRepository
    @Inject
    constructor(
        private val lyricsSource: LyricsSource,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
    ) : LyricsRepository {
        override suspend fun lyrics(trackId: MediaId): MqResult<Lyrics?> {
            // Signed out meanwhile: nothing to do, and not a session expiry.
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            return lyricsSource.lyrics(server, trackId).also {
                if ((it as? MqResult.Failure)?.error == MqError.Unauthorized) {
                    withContext(NonCancellable) { sessionRepository.onUnauthorized() }
                }
            }
        }
    }
