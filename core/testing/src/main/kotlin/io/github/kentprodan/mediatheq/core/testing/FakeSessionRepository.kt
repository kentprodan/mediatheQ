package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow

/** [SessionRepository] with settable results; successful logins switch [session] to logged in. */
class FakeSessionRepository : SessionRepository {
    override val session = MutableStateFlow<SessionState>(SessionState.LoggedOut())

    var serverCheck: ServerCheck =
        ServerCheck.Supported(
            "http://nas:8096",
            ServerInfo(ServerId("server-1"), "Home", "12.2.0"),
        )
    var login: MqResult<Unit> = MqResult.Success(Unit)
    var quickConnectEnabled: MqResult<Boolean> = MqResult.Success(true)
    var quickConnectCode: MqResult<QuickConnectCode> = MqResult.Success(QuickConnectCode("123456", "secret"))

    /** Answers to successive approval checks; the last one repeats. */
    var quickConnectApprovals: List<MqResult<Boolean>> = listOf(MqResult.Success(false))
    var approvalChecks = 0
        private set
    val checkedAddresses = mutableListOf<String>()

    override suspend fun checkServer(address: String) = serverCheck.also { checkedAddresses += address }

    override suspend fun login(
        baseUrl: String,
        serverName: String,
        userName: String,
        password: String,
    ) = login.also { if (it is MqResult.Success) session.value = SessionState.LoggedIn(serverName, userName) }

    override suspend fun isQuickConnectEnabled(baseUrl: String) = quickConnectEnabled

    override suspend fun startQuickConnect(baseUrl: String) = quickConnectCode

    override suspend fun isQuickConnectApproved(
        baseUrl: String,
        code: QuickConnectCode,
    ) = quickConnectApprovals[minOf(approvalChecks++, quickConnectApprovals.lastIndex)]

    override suspend fun loginWithQuickConnect(
        baseUrl: String,
        serverName: String,
        code: QuickConnectCode,
    ) = login.also { if (it is MqResult.Success) session.value = SessionState.LoggedIn(serverName, "Ana") }

    override suspend fun logout() {
        session.value = SessionState.LoggedOut()
    }

    override suspend fun onUnauthorized() {
        session.value = SessionState.LoggedOut(LogoutReason.SessionExpired)
    }
}
