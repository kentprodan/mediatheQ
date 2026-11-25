package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import io.github.kentprodan.mediatheq.core.server.SignedInServer
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
        override suspend fun instantMix(seed: MediaId): MqResult<List<Track>> =
            when (val ids = ask { source.instantMix(it, seed, MIX_SIZE) }) {
                is MqResult.Failure -> {
                    ids
                }

                is MqResult.Success -> {
                    // Filtered like every list (offline: only downloads); a track seed first.
                    MqResult.Success(library.tracksById((listOf(seed) + ids.value).distinct()))
                }
            }

        override suspend fun similarAlbums(albumId: MediaId): MqResult<List<Album>> =
            when (val ids = ask { source.similarAlbums(it, albumId, SIMILAR_ALBUMS) }) {
                is MqResult.Failure -> {
                    ids
                }

                is MqResult.Success -> {
                    MqResult.Success(library.albumsById(ids.value.filter { it != albumId }))
                }
            }

        override suspend fun similarArtists(artistId: MediaId): MqResult<List<Artist>> =
            when (val ids = ask { source.similarArtists(it, artistId, SIMILAR_ARTISTS) }) {
                is MqResult.Failure -> {
                    ids
                }

                is MqResult.Success -> {
                    // shortcut: one Room read per artist (12 at most); a batch query if this row grows.
                    MqResult.Success(ids.value.filter { it != artistId }.mapNotNull { library.artist(it).first() })
                }
            }

        /** [request] on the signed-in server; a 401 signs out. Signed out: Unauthorized, nothing asked. */
        private suspend fun ask(request: suspend (SignedInServer) -> MqResult<List<MediaId>>): MqResult<List<MediaId>> {
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            return request(server).also {
                if ((it as? MqResult.Failure)?.error == MqError.Unauthorized) {
                    withContext(NonCancellable) { sessionRepository.onUnauthorized() }
                }
            }
        }

        private companion object {
            /** About five hours of music: the queue a radio starts with. */
            const val MIX_SIZE = 100

            /** One row of cards on the album page. */
            const val SIMILAR_ALBUMS = 12

            /** One row of artists on the artist page. */
            const val SIMILAR_ARTISTS = 12
        }
    }
