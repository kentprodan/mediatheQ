package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import kotlinx.coroutines.flow.Flow

/** Signing in to and out of a media server. One server for now. */
interface SessionRepository {
    /** The current session; emits again on every login, logout and expiry. */
    val session: Flow<SessionState>

    /** Normalizes [address], checks the cleartext rule and asks the server who it is. */
    suspend fun checkServer(address: String): ServerCheck

    /** Username + password login; on success [session] becomes [SessionState.LoggedIn]. */
    suspend fun login(
        baseUrl: String,
        serverName: String,
        userName: String,
        password: String,
    ): MqResult<Unit>

    /** Whether the server at [baseUrl] allows Quick Connect. */
    suspend fun isQuickConnectEnabled(baseUrl: String): MqResult<Boolean>

    /** Starts Quick Connect: the user approves the returned code in another client. */
    suspend fun startQuickConnect(baseUrl: String): MqResult<QuickConnectCode>

    /** Whether [code] was approved; `NotFound` when it expired. */
    suspend fun isQuickConnectApproved(
        baseUrl: String,
        code: QuickConnectCode,
    ): MqResult<Boolean>

    /** Logs in with an approved [code]. */
    suspend fun loginWithQuickConnect(
        baseUrl: String,
        serverName: String,
        code: QuickConnectCode,
    ): MqResult<Unit>

    /** Signs out locally, also when the server cannot be reached. */
    suspend fun logout()

    /** Called on any 401 from the server: signs out with [LogoutReason.SessionExpired]. */
    suspend fun onUnauthorized()
}
