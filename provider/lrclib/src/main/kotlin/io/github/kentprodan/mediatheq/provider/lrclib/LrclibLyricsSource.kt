package io.github.kentprodan.mediatheq.provider.lrclib

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.server.OnlineLyricsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * LRCLIB (#381): the exact match first (`api/get` with title, artist, album and length), then a search that accepts
 * only a length within 5 s; synced lyrics before plain ones, instrumental tracks skipped. Up to three tries, only on
 * transient answers (no connection, 429, 5xx); 404 is "none". Only the names and the length leave the device, with a
 * User-Agent naming the app. The plain OkHttp client: no media server token ever goes along.
 */
internal class LrclibLyricsSource(
    private val client: OkHttpClient,
    private val base: HttpUrl,
    private val retryDelay: Duration,
) : OnlineLyricsSource {
    @Inject
    constructor(client: OkHttpClient) : this(client, "https://lrclib.net/".toHttpUrl(), 1.seconds)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lyrics(
        title: String,
        artist: String,
        album: String?,
        duration: Duration,
    ): MqResult<Lyrics?> {
        if (title.isBlank() || artist.isBlank()) return MqResult.Success(null)
        if (!album.isNullOrBlank() && duration.isPositive()) {
            val exact =
                get(
                    url("api/get") {
                        addQueryParameter("track_name", title)
                        addQueryParameter("artist_name", artist)
                        addQueryParameter("album_name", album)
                        addQueryParameter("duration", duration.inWholeSeconds.toString())
                    },
                )
            if (exact is MqResult.Failure) return exact
            val record = (exact as MqResult.Success).value?.let { decode(it, LrclibTrack.serializer()) }
            // The exact track is instrumental: a search would only find another version's words.
            if (record?.instrumental == true) return MqResult.Success(null)
            record?.lyrics()?.let { return MqResult.Success(it) }
        }
        val found =
            get(
                url("api/search") {
                    addQueryParameter("track_name", title)
                    addQueryParameter("artist_name", artist)
                },
            )
        if (found is MqResult.Failure) return found
        val tracks =
            (found as MqResult.Success).value?.let { decode(it, ListSerializer(LrclibTrack.serializer())) }.orEmpty()
        return MqResult.Success(best(tracks, duration)?.lyrics())
    }

    private fun url(
        path: String,
        query: HttpUrl.Builder.() -> Unit,
    ): HttpUrl =
        base
            .newBuilder()
            .addPathSegments(path)
            .apply(query)
            .build()

    /** The body of a 200, `null` for a 404; transient failures are tried again. */
    private suspend fun get(url: HttpUrl): MqResult<String?> {
        var attempt = 1
        while (true) {
            val answer = once(url)
            val error = (answer as? MqResult.Failure)?.error
            val transient = error == MqError.Network || error is MqError.Server
            if (!transient || attempt == ATTEMPTS) return answer
            attempt++
            delay(retryDelay)
        }
    }

    private suspend fun once(url: HttpUrl): MqResult<String?> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            MqResult.Success(response.body.string())
                        }

                        response.code == NOT_FOUND -> {
                            MqResult.Success(null)
                        }

                        // Too many requests waits like a server error.
                        response.code == TOO_MANY || response.code >= SERVER_ERROR -> {
                            MqResult.Failure(MqError.Server(response.code))
                        }

                        else -> {
                            MqResult.Failure(MqError.Client(response.code, null))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                MqResult.Failure(MqError.Network)
            }
        }

    private fun <T> decode(
        body: String,
        serializer: KSerializer<T>,
    ): T? = runCatching { json.decodeFromString(serializer, body) }.getOrNull()

    private companion object {
        const val ATTEMPTS = 3
        const val NOT_FOUND = 404
        const val TOO_MANY = 429
        const val SERVER_ERROR = 500
        const val TOLERANCE_SECONDS = 5.0
        const val USER_AGENT = "mediatheQ (https://github.com/kentprodan/mediatheQ)"

        /** The first usable track within the length tolerance, a synced one first. */
        fun best(
            tracks: List<LrclibTrack>,
            duration: Duration,
        ): LrclibTrack? {
            val seconds = duration.inWholeMilliseconds / 1000.0
            val usable =
                tracks.filter { track ->
                    val length = track.duration
                    track.lyrics() != null &&
                        (!duration.isPositive() || length == null || abs(length - seconds) <= TOLERANCE_SECONDS)
                }
            return usable.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: usable.firstOrNull()
        }
    }
}

/** One LRCLIB record; only the fields the app reads. */
@Serializable
internal data class LrclibTrack(
    val instrumental: Boolean = false,
    val duration: Double? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
) {
    /** Synced when there are some, else plain; `null` for an instrumental or an empty record. */
    fun lyrics(): Lyrics? = if (instrumental) null else syncedLyrics?.let(::parseLrc) ?: plainLyrics?.let(::plainLyrics)
}
