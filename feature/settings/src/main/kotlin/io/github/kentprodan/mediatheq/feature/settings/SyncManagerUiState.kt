package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.SyncRecord
import io.github.kentprodan.mediatheq.core.data.SyncStep
import kotlin.time.Duration

/** Settings -> Sync manager (#148). */
sealed interface SyncManagerUiState {
    data object Loading : SyncManagerUiState

    data class Content(
        /** In the user's order. */
        val cards: List<SyncCard>,
        /** Why Sync all is disabled, `null` while it can run. */
        val offline: OfflineReason?,
        /** A sync of the active account is running. */
        val syncing: Boolean,
    ) : SyncManagerUiState
}

/** One account in the Sync manager. */
data class SyncCard(
    val id: String,
    val serverName: String,
    val userName: String,
    val baseUrl: String,
    val active: Boolean,
    val connection: Connection,
    /** The last sync and how long ago it ended; `null` before the first one. */
    val last: SyncRecord?,
    val ago: Duration?,
    /** The sync running now (active account only). */
    val running: SyncProgress?,
)

data class SyncProgress(
    val step: SyncStep,
    val fetched: Int,
    val total: Int,
)

enum class Connection { Checking, Online, Offline }

enum class OfflineReason { NoNetwork, OfflineMode, Unreachable }

sealed interface SyncManagerAction {
    /** Syncs the active account in full; the others sync when they become active (ADR 0036). */
    data object SyncAll : SyncManagerAction

    /** Asks account [id]'s server again whether it answers. */
    data class Retry(
        val id: String,
    ) : SyncManagerAction
}
