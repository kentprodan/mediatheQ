package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.getOrNull
import io.github.kentprodan.mediatheq.core.common.isLocalNetwork
import io.github.kentprodan.mediatheq.core.database.AccountDatabases
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class DefaultSessionRepository
    @Inject
    constructor(
        private val authSource: AuthSource,
        private val accountStore: AccountStore,
        private val databases: AccountDatabases,
        private val restarter: AppRestarter,
        private val queue: QueueRepository,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val log: AppLog,
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

        override val activeServerId: Flow<ServerId?> =
            accountStore.account.map { it.getOrNull()?.serverId }.distinctUntilChanged()

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
            // Local first, so leaving the screen or a slow server can never keep the token.
            clearAccount(reason = null)
            if (account == null) return
            // Best effort: the server may be offline or may have revoked the token already.
            withContext(NonCancellable) {
                withTimeoutOrNull(LOGOUT_TIMEOUT) {
                    authSource.logout(connection(account.baseUrl).copy(accessToken = account.accessToken))
                }
            }
        }

        override suspend fun onUnauthorized() = clearAccount(LogoutReason.SessionExpired)

        private suspend fun clearAccount(reason: LogoutReason?) {
            log.info(TAG, if (reason == null) "Signed out" else "Signed out: $reason")
            try {
                accountStore.clear()
            } catch (_: IOException) {
                // Nothing better to do; the session keeps showing what is on disk.
            }
            logoutReason.value = reason
            // Nothing is left to resume once signed out (#43).
            try {
                queue.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The flag is already off, so a media button starts nothing; the rows go with the next save.
            }
        }

        private suspend fun connection(baseUrl: String) = ServerConnection(baseUrl, accountStore.deviceId())

        /**
         * Stores a successful login, replacing any older token of the same server and user (R9). With no active account
         * it becomes active: a new one takes the open (empty) database, a saved one is opened by a restart. With one
         * active, a new account is added beside it (#147).
         */
        private suspend fun save(
            baseUrl: String,
            serverName: String,
            result: MqResult<AuthSession>,
        ): MqResult<Unit> {
            val session =
                (result as? MqResult.Success)?.value
                    ?: return (result as MqResult.Failure).also {
                        log.warn(
                            TAG,
                            "Sign-in to $serverName failed: ${it.error}",
                        )
                    }
            return try {
                // Signed-out entries too (#150): signing in to a restored account keeps its id.
                val saved = accountStore.saved.first()
                val existing = saved.firstOrNull { it.serverId == session.serverId && it.userId == session.userId }
                val noneActive = saved.none { it.id == databases.open && it.signedIn }
                val id = existing?.id ?: if (noneActive) databases.open else databases.newName()
                accountStore.save(
                    StoredAccount(
                        baseUrl = baseUrl,
                        serverId = session.serverId,
                        serverName = serverName,
                        userId = session.userId,
                        userName = session.userName,
                        accessToken = session.accessToken,
                        id = id,
                    ),
                )
                logoutReason.value = null
                log.info(TAG, "Signed in to $serverName as ${session.userName}")
                if (noneActive && id != databases.open && databases.select(id)) restarter.restart()
                MqResult.Success(Unit)
            } catch (_: GeneralSecurityException) {
                MqResult.Failure(MqError.Storage)
            } catch (_: IOException) {
                MqResult.Failure(MqError.Storage)
            }
        }

        private companion object {
            const val TAG = "Session"
            val LOGOUT_TIMEOUT = 5.seconds
        }
    }
