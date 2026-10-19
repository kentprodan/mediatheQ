package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream

/**
 * The one place Jellyfin requests are built, sent and mapped to [MqError] (jellyfin skill, sections 2 and 11). GETs are
 * idempotent and retried on network errors and 5xx; writes are never retried blindly.
 */
internal class JellyfinHttp(
    okHttpClient: OkHttpClient,
    clientInfo: ClientInfo,
    val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val client = okHttpClient.newBuilder().addInterceptor(JellyfinAuthInterceptor(clientInfo)).build()

    /** GET [path] and parse the body with [serializer]; [map] returning `null` means an unusable response. */
    suspend fun <Dto, R : Any> get(
        connection: ServerConnection,
        path: String,
        serializer: KSerializer<Dto>,
        query: List<Pair<String, String>> = emptyList(),
        map: (Dto) -> R?,
    ): MqResult<R> {
        var attempt = 1
        while (true) {
            val result = send(connection, path, query, body = null) { parse(it, path, serializer, map) }
            val retryable = (result as? MqResult.Failure)?.error.let { it == MqError.Network || it is MqError.Server }
            if (!retryable || attempt == GET_ATTEMPTS) return result
            delay(RETRY_DELAY_MILLIS * attempt)
            attempt++
        }
    }

    /** POST a JSON [body] (or an empty one) and parse the answer with [serializer]. */
    suspend fun <Dto, R : Any> post(
        connection: ServerConnection,
        path: String,
        serializer: KSerializer<Dto>,
        body: RequestBody = emptyBody(),
        map: (Dto) -> R?,
    ): MqResult<R> = send(connection, path, emptyList(), body) { parse(it, path, serializer, map) }

    /** POST without a response body. */
    suspend fun post(
        connection: ServerConnection,
        path: String,
    ): MqResult<Unit> = send(connection, path, emptyList(), emptyBody()) { MqResult.Success(Unit) }

    fun <T> jsonBody(
        serializer: KSerializer<T>,
        value: T,
    ): RequestBody = json.encodeToString(serializer, value).toRequestBody(JSON)

    private fun emptyBody(): RequestBody = ByteArray(0).toRequestBody()

    private fun <Dto, R : Any> parse(
        body: InputStream,
        path: String,
        serializer: KSerializer<Dto>,
        map: (Dto) -> R?,
    ): MqResult<R> {
        val value = map(json.decodeFromStream(serializer, body))
        return if (value !=
            null
        ) {
            MqResult.Success(value)
        } else {
            MqResult.Failure(MqError.Unknown(UnexpectedResponse(path)))
        }
    }

    private suspend fun <R> send(
        connection: ServerConnection,
        path: String,
        query: List<Pair<String, String>>,
        body: RequestBody?,
        onSuccess: (InputStream) -> MqResult<R>,
    ): MqResult<R> {
        val url = url(connection, path, query) ?: return MqResult.Failure(MqError.Client(0, "invalid server address"))
        val request =
            Request
                .Builder()
                .url(url)
                .apply { if (body != null) post(body) }
                .tag(ServerConnection::class, connection)
                .build()
        return withContext(ioDispatcher) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSuccess(response.body.byteStream())
                    } else {
                        MqResult.Failure(httpError(response.code))
                    }
                }
            } catch (_: IOException) {
                MqResult.Failure(MqError.Network)
            } catch (e: SerializationException) {
                MqResult.Failure(MqError.Unknown(e))
            } catch (e: IllegalArgumentException) {
                // kotlinx.serialization reports some malformed JSON this way.
                MqResult.Failure(MqError.Unknown(e))
            }
        }
    }

    private fun url(
        connection: ServerConnection,
        path: String,
        query: List<Pair<String, String>>,
    ): HttpUrl? =
        connection.baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(path)
            ?.apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            ?.build()

    private companion object {
        val JSON = "application/json".toMediaType()
        const val GET_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 500L
    }
}

/** HTTP status to [MqError] (jellyfin skill, section 11). */
internal fun httpError(code: Int): MqError =
    when (code) {
        401 -> MqError.Unauthorized
        403 -> MqError.Forbidden
        404 -> MqError.NotFound
        in 500..599 -> MqError.Server(code)
        else -> MqError.Client(code)
    }

/** A 2xx response whose body is not what the endpoint documents. */
internal class UnexpectedResponse(
    path: String,
) : Exception("Unexpected response from $path")
