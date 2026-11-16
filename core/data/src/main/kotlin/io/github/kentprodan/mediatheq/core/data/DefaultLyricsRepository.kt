package io.github.kentprodan.mediatheq.core.data

import androidx.collection.LruCache
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LyricsSource
import io.github.kentprodan.mediatheq.core.server.OnlineLyricsSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The server's lyrics; when it has none and Settings -> Playback -> Lyrics from LRCLIB is on (#381), the online
 * source's, looked up by the track's names. An online answer (also "none") is kept for the app's lifetime, so a track
 * played again asks nothing; a failure is not kept.
 */
class DefaultLyricsRepository
    @Inject
    constructor(
        private val lyricsSource: LyricsSource,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
        private val online: OnlineLyricsSource,
        private val audioPreferences: AudioPreferencesRepository,
        private val library: LibraryRepository,
    ) : LyricsRepository {
        private val found = LruCache<MediaId, OnlineAnswer>(ONLINE_CACHE)

        override suspend fun lyrics(trackId: MediaId): MqResult<Lyrics?> {
            // Signed out meanwhile: nothing to do, and not a session expiry.
            val server = accountStore.signedInServer() ?: return MqResult.Failure(MqError.Unauthorized)
            val fromServer =
                lyricsSource.lyrics(server, trackId).also {
                    if ((it as? MqResult.Failure)?.error == MqError.Unauthorized) {
                        withContext(NonCancellable) { sessionRepository.onUnauthorized() }
                    }
                }
            if (fromServer !is MqResult.Success || fromServer.value != null) return fromServer
            if (!audioPreferences.preferences
                    .first()
                    .behavior.onlineLyrics
            ) {
                return fromServer
            }
            return onlineLyrics(trackId)?.let { MqResult.Success(it) } ?: fromServer
        }

        /** The online lyrics of [trackId], `null` when there are none or the service could not answer. */
        private suspend fun onlineLyrics(trackId: MediaId): Lyrics? {
            found[trackId]?.let { return it.lyrics }
            val track = library.track(trackId).first() ?: return null
            val artist = track.artists.firstOrNull()?.name ?: return null
            val answer = online.lyrics(track.title, artist, track.albumTitle, track.duration)
            return (answer as? MqResult.Success)?.value.also {
                if (answer is MqResult.Success) found.put(trackId, OnlineAnswer(it))
            }
        }

        /** A kept answer, "none" included (an LruCache holds no nulls). */
        private class OnlineAnswer(
            val lyrics: Lyrics?,
        )

        private companion object {
            const val ONLINE_CACHE = 200
        }
    }
