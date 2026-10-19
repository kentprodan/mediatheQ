package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

/** [AuthSource] for Jellyfin 12 (jellyfin skill, section 2). Routes checked against the 12.2.0 OpenAPI copy. */
class JellyfinAuthSource internal constructor(
    private val http: JellyfinHttp,
) : AuthSource {
    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(JellyfinHttp(okHttpClient, clientInfo, json, ioDispatcher))

    override suspend fun serverInfo(connection: ServerConnection): MqResult<ServerInfo> =
        http.get(connection, "System/Info/Public", PublicSystemInfoDto.serializer()) { it.toDomain() }

    override suspend fun login(
        connection: ServerConnection,
        userName: String,
        password: String,
    ): MqResult<AuthSession> =
        http.post(
            connection,
            "Users/AuthenticateByName",
            AuthenticationResultDto.serializer(),
            http.jsonBody(AuthenticateUserByNameDto.serializer(), AuthenticateUserByNameDto(userName, password)),
        ) { it.toDomain() }

    override suspend fun isQuickConnectEnabled(connection: ServerConnection): MqResult<Boolean> =
        http.get(connection, "QuickConnect/Enabled", Boolean.serializer()) { it }

    override suspend fun startQuickConnect(connection: ServerConnection): MqResult<QuickConnectCode> =
        http.post(connection, "QuickConnect/Initiate", QuickConnectResultDto.serializer()) { it.toDomain() }

    override suspend fun isQuickConnectApproved(
        connection: ServerConnection,
        code: QuickConnectCode,
    ): MqResult<Boolean> =
        http.get(
            connection,
            "QuickConnect/Connect",
            QuickConnectResultDto.serializer(),
            listOf("secret" to code.secret),
        ) { it.authenticated }

    override suspend fun loginWithQuickConnect(
        connection: ServerConnection,
        code: QuickConnectCode,
    ): MqResult<AuthSession> =
        http.post(
            connection,
            "Users/AuthenticateWithQuickConnect",
            AuthenticationResultDto.serializer(),
            http.jsonBody(QuickConnectDto.serializer(), QuickConnectDto(code.secret)),
        ) { it.toDomain() }

    override suspend fun logout(connection: ServerConnection): MqResult<Unit> = http.post(connection, "Sessions/Logout")
}
