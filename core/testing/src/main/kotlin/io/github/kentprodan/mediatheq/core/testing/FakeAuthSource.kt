package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.awaitCancellation

/** [AuthSource] with settable results; [connections] records every call's connection. */
class FakeAuthSource : AuthSource {
    var serverInfo: MqResult<ServerInfo> = MqResult.Success(ServerInfo(ServerId("server-1"), "Home", "12.2.0"))
    var login: MqResult<AuthSession> = MqResult.Success(session("token-1"))
    var quickConnectEnabled: MqResult<Boolean> = MqResult.Success(true)
    var quickConnectCode: MqResult<QuickConnectCode> = MqResult.Success(QuickConnectCode("123456", "secret"))
    var quickConnectApproved: MqResult<Boolean> = MqResult.Success(false)
    var logout: MqResult<Unit> = MqResult.Success(Unit)
    val connections = mutableListOf<ServerConnection>()

    override suspend fun serverInfo(connection: ServerConnection) = serverInfo.also { connections += connection }

    override suspend fun login(
        connection: ServerConnection,
        userName: String,
        password: String,
    ) = login.also { connections += connection }

    override suspend fun isQuickConnectEnabled(connection: ServerConnection) =
        quickConnectEnabled.also { connections += connection }

    override suspend fun startQuickConnect(connection: ServerConnection) =
        quickConnectCode.also {
            connections +=
                connection
        }

    override suspend fun isQuickConnectApproved(
        connection: ServerConnection,
        code: QuickConnectCode,
    ) = quickConnectApproved.also { connections += connection }

    override suspend fun loginWithQuickConnect(
        connection: ServerConnection,
        code: QuickConnectCode,
    ) = login.also { connections += connection }

    /** When true, logout never answers, like a server that hangs. */
    var logoutHangs = false

    override suspend fun logout(connection: ServerConnection): MqResult<Unit> {
        connections += connection
        if (logoutHangs) awaitCancellation()
        return logout
    }

    companion object {
        /** A successful login result carrying [token]. */
        fun session(token: String) = AuthSession(ServerId("server-1"), "user-1", "Ana", token)
    }
}
