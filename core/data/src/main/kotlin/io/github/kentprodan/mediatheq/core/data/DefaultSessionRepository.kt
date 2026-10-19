package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.isLocalNetwork
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSessionRepository
    @Inject
    constructor(
        private val authSource: AuthSource,
        private val accountStore: AccountStore,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SessionRepository {
        // Why the app signed out on its own; in memory only, a restart shows the plain onboarding.
        private val logoutReason = MutableStateFlow<LogoutReason?>(null)

        override val session: Flow<SessionState> =
            combine(accountStore.account, logoutReason) { account, reason ->
                when (account) {
                    is MqResult.Failure -> {
                        SessionState.LoggedOut(LogoutReason.StorageError)
                    }

                    is MqResult.Success -> {
                        account.value?.let { SessionState.LoggedIn(it.serverName, it.userName) }
                            ?: SessionState.LoggedOut(reason)
                    }
                }
            }

        override suspend fun checkServer(address: String): ServerCheck {
            val baseUrl = normalizeServerAddress(address) ?: return ServerCheck.InvalidAddress
            if (baseUrl.startsWith("http://")) {
                val host = URI(baseUrl).host.removeSurrounding("[", "]")
                val addresses =
                    try {
                        withContext(ioDispatcher) { InetAddress.getAllByName(host).toList() }
                    } catch (_: UnknownHostException) {
                        return ServerCheck.Failed(MqError.Network)
                    }
                if (!addresses.all(InetAddress::isLocalNetwork)) return ServerCheck.CleartextNotAllowed
            }
            return when (val result = authSource.serverInfo(connection(baseUrl))) {
                is MqResult.Failure -> {
                    ServerCheck.Failed(result.error)
                }

                is MqResult.Success -> {
                    if (result.value.isSupported) {
                        ServerCheck.Supported(baseUrl, result.value)
                    } else {
                        ServerCheck.Unsupported(result.value)
                    }
                }
            }
        }

        override suspend fun login(
            baseUrl: String,
            serverName: String,
            userName: String,
            password: String,
        ): MqResult<Unit> = save(baseUrl, serverName, authSource.login(connection(baseUrl), userName, password))

        override suspend fun isQuickConnectEnabled(baseUrl: String): MqResult<Boolean> =
            authSource.isQuickConnectEnabled(connection(baseUrl))

        override suspend fun startQuickConnect(baseUrl: String): MqResult<QuickConnectCode> =
            authSource.startQuickConnect(connection(baseUrl))

        override suspend fun isQuickConnectApproved(
            baseUrl: String,
            code: QuickConnectCode,
        ): MqResult<Boolean> = authSource.isQuickConnectApproved(connection(baseUrl), code)

        override suspend fun loginWithQuickConnect(
            baseUrl: String,
            serverName: String,
            code: QuickConnectCode,
        ): MqResult<Unit> = save(baseUrl, serverName, authSource.loginWithQuickConnect(connection(baseUrl), code))

        override suspend fun logout() {
            val account = (accountStore.account.first() as? MqResult.Success)?.value
            // Best effort: the local token goes away even when the server is offline or already revoked it.
            if (account != null) authSource.logout(connection(account.baseUrl).copy(accessToken = account.accessToken))
            accountStore.clear()
            logoutReason.value = null
        }

        override suspend fun onUnauthorized() {
            accountStore.clear()
            logoutReason.value = LogoutReason.SessionExpired
        }

        private suspend fun connection(baseUrl: String) = ServerConnection(baseUrl, accountStore.deviceId())

        /** Stores a successful login, replacing any older token (R9). */
        private suspend fun save(
            baseUrl: String,
            serverName: String,
            result: MqResult<AuthSession>,
        ): MqResult<Unit> {
            val session = (result as? MqResult.Success)?.value ?: return result as MqResult.Failure
            return try {
                accountStore.save(
                    StoredAccount(
                        baseUrl = baseUrl,
                        serverId = session.serverId,
                        serverName = serverName,
                        userId = session.userId,
                        userName = session.userName,
                        accessToken = session.accessToken,
                    ),
                )
                logoutReason.value = null
                MqResult.Success(Unit)
            } catch (_: GeneralSecurityException) {
                MqResult.Failure(MqError.Storage)
            }
        }
    }
