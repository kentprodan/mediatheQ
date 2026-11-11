package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.OfflineFilter
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** Settings -> Sync manager (#148): each account's last sync and connection, and Sync all. */
@HiltViewModel
class SyncManagerViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
        private val sync: LibrarySyncRepository,
        private val session: SessionRepository,
        network: NetworkMonitor,
        offlineFilter: OfflineFilter,
    ) : ViewModel() {
        private val connections = MutableStateFlow<Map<String, Connection>>(emptyMap())
        private val checks = mutableMapOf<String, Job>()

        /** No network, or offline mode on: the servers are not asked at all. */
        private val deviceOffline =
            combine(network.online, offlineFilter.forced) { online, forced ->
                when {
                    !online -> OfflineReason.NoNetwork
                    forced -> OfflineReason.OfflineMode
                    else -> null
                }
            }.distinctUntilChanged()

        /** Asks every server while the sheet shows (each time it opens) and again when the network comes back. */
        private val checkedWhileShown =
            deviceOffline.onEach { offline -> if (offline == null) accounts.accounts.first().forEach(::check) }

        val uiState: StateFlow<SyncManagerUiState> =
            combine(accounts.accounts, sync.status, checkedWhileShown, connections) { list, status, offline, checked ->
                val cards =
                    list.map { account ->
                        SyncCard(
                            id = account.id,
                            serverName = account.serverName,
                            userName = account.userName,
                            baseUrl = account.baseUrl,
                            active = account.active,
                            connection =
                                if (offline !=
                                    null
                                ) {
                                    Connection.Offline
                                } else {
                                    checked[account.id] ?: Connection.Checking
                                },
                            last = account.lastSync,
                            running =
                                (status as? SyncStatus.Running)?.takeIf { account.active }?.let {
                                    SyncProgress(it.step, it.fetched, it.total)
                                },
                        )
                    }
                val unreachable = cards.any { it.active && it.connection == Connection.Offline }
                SyncManagerUiState.Content(
                    cards = cards,
                    offline = offline ?: OfflineReason.Unreachable.takeIf { unreachable },
                    syncing = status is SyncStatus.Running,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncManagerUiState.Loading)

        fun onAction(action: SyncManagerAction) {
            when (action) {
                // The sync runs in the app's scope: closing the sheet does not stop it.
                SyncManagerAction.SyncAll -> {
                    viewModelScope.launch { sync.sync() }
                }

                is SyncManagerAction.Retry -> {
                    viewModelScope.launch {
                        if (deviceOffline.first() != null) return@launch
                        accounts.accounts
                            .first()
                            .firstOrNull { it.id == action.id }
                            ?.let(::check)
                    }
                }
            }
        }

        /** Asks [account]'s server; a newer check of the same account replaces one still waiting. */
        private fun check(account: Account) {
            checks[account.id]?.cancel()
            connections.update { it + (account.id to Connection.Checking) }
            checks[account.id] =
                viewModelScope.launch {
                    val check = withTimeoutOrNull(CHECK_TIMEOUT) { session.checkServer(account.baseUrl) }
                    val answered = check is ServerCheck.Supported || check is ServerCheck.Unsupported
                    connections.update { it + (account.id to if (answered) Connection.Online else Connection.Offline) }
                }
        }

        private companion object {
            val CHECK_TIMEOUT = 10.seconds
        }
    }
