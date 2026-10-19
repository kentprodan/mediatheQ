package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerInfo

/** Login and logout against one kind of media server. Failures are [io.github.kentprodan.mediatheq.core.common.MqError]s. */
interface AuthSource {
    /** Public server facts; needs no login. */
    suspend fun serverInfo(connection: ServerConnection): MqResult<ServerInfo>

    /** Username + password login. Wrong credentials fail with `Unauthorized`. */
    suspend fun login(
        connection: ServerConnection,
        userName: String,
        password: String,
    ): MqResult<AuthSession>

    /** Whether the server allows Quick Connect. */
    suspend fun isQuickConnectEnabled(connection: ServerConnection): MqResult<Boolean>

    /** Starts a Quick Connect request. */
    suspend fun startQuickConnect(connection: ServerConnection): MqResult<QuickConnectCode>

    /** Whether the user approved [code] yet. An expired or unknown code fails with `NotFound`. */
    suspend fun isQuickConnectApproved(
        connection: ServerConnection,
        code: QuickConnectCode,
    ): MqResult<Boolean>

    /** Logs in with an approved [code]. */
    suspend fun loginWithQuickConnect(
        connection: ServerConnection,
        code: QuickConnectCode,
    ): MqResult<AuthSession>

    /** Ends the session of [connection]'s token on the server. */
    suspend fun logout(connection: ServerConnection): MqResult<Unit>
}
