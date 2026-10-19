package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.AuthenticateUserByNameDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.AuthenticationResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.PublicSystemInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QuickConnectDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.QuickConnectResultDto
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/** [AuthSource] for Jellyfin 12 (jellyfin skill, section 2). Routes checked against the 12.2.0 OpenAPI copy. */
class JellyfinAuthSource
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        private val json: Json,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AuthSource {
        private val client = okHttpClient.newBuilder().addInterceptor(JellyfinAuthInterceptor(clientInfo)).build()

        override suspend fun serverInfo(connection: ServerConnection): MqResult<ServerInfo> =
            call<PublicSystemInfoDto, ServerInfo>(connection, "System/Info/Public") { it.toDomain() }

        override suspend fun login(
            connection: ServerConnection,
            userName: String,
            password: String,
        ): MqResult<AuthSession> =
            call<AuthenticationResultDto, AuthSession>(
                connection,
                "Users/AuthenticateByName",
                body = jsonBody(AuthenticateUserByNameDto(userName, password)),
            ) { it.toDomain() }

        override suspend fun isQuickConnectEnabled(connection: ServerConnection): MqResult<Boolean> =
            call<Boolean, Boolean>(connection, "QuickConnect/Enabled") { it }

        override suspend fun startQuickConnect(connection: ServerConnection): MqResult<QuickConnectCode> =
            call<QuickConnectResultDto, QuickConnectCode>(connection, "QuickConnect/Initiate", body = emptyBody()) {
                it.toDomain()
            }

        override suspend fun isQuickConnectApproved(
            connection: ServerConnection,
            code: QuickConnectCode,
        ): MqResult<Boolean> =
            call<QuickConnectResultDto, Boolean>(connection, "QuickConnect/Connect", "secret" to code.secret) {
                it.authenticated
            }

        override suspend fun loginWithQuickConnect(
            connection: ServerConnection,
            code: QuickConnectCode,
        ): MqResult<AuthSession> =
            call<AuthenticationResultDto, AuthSession>(
                connection,
                "Users/AuthenticateWithQuickConnect",
                body = jsonBody(QuickConnectDto(code.secret)),
            ) { it.toDomain() }

        override suspend fun logout(connection: ServerConnection): MqResult<Unit> =
            execute(connection, "Sessions/Logout", body = emptyBody()) { MqResult.Success(Unit) }

        private inline fun <reified T> jsonBody(value: T): RequestBody =
            json.encodeToString(serializer<T>(), value).toRequestBody(JSON)

        private fun emptyBody(): RequestBody = ByteArray(0).toRequestBody()

        /** GET (or POST with [body]) that parses a [Dto]; a response we cannot map fails with [MqError.Unknown]. */
        private suspend inline fun <reified Dto, R : Any> call(
            connection: ServerConnection,
            path: String,
            query: Pair<String, String>? = null,
            body: RequestBody? = null,
            crossinline map: (Dto) -> R?,
        ): MqResult<R> =
            execute(connection, path, query, body) { text ->
                val value = map(json.decodeFromString(serializer<Dto>(), text))
                if (value !=
                    null
                ) {
                    MqResult.Success(value)
                } else {
                    MqResult.Failure(MqError.Unknown(UnexpectedResponse(path)))
                }
            }

        private suspend fun <R> execute(
            connection: ServerConnection,
            path: String,
            query: Pair<String, String>? = null,
            body: RequestBody? = null,
            onSuccess: (String) -> MqResult<R>,
        ): MqResult<R> {
            val url =
                connection.baseUrl
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments(path)
                    ?.apply { query?.let { addQueryParameter(it.first, it.second) } }
                    ?.build() ?: return MqResult.Failure(MqError.Client(0, "invalid server address"))
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
                            onSuccess(response.body.string())
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

        private companion object {
            val JSON = "application/json".toMediaType()
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
