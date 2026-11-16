package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultSimilarRepository
    @Inject
    constructor(
        private val source: LibrarySource,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
        private val library: LibraryRepository,
    ) : SimilarRepository {
        override suspend fun instantMix(seed: MediaId): MqResult<List<Track>> {
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            val ids =
                when (val answer = source.instantMix(server, seed, MIX_SIZE)) {
                    is MqResult.Success -> {
                        answer.value
                    }

                    is MqResult.Failure -> {
                        if (answer.error == MqError.Unauthorized) {
                            withContext(NonCancellable) { sessionRepository.onUnauthorized() }
                        }
                        return answer
                    }
                }
            // The library's tracks, filtered like every list (offline: only downloads).
            val tracks = library.tracks().first().associateBy { it.id }
            val mix = (listOf(seed) + ids).distinct().mapNotNull { tracks[it] }
            return MqResult.Success(mix)
        }

        private companion object {
            /** About five hours of music: the queue a radio starts with. */
            const val MIX_SIZE = 100
        }
    }
