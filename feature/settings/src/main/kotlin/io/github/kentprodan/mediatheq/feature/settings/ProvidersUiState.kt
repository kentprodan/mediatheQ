package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.Account

/** Settings -> Manage media providers (#147). */
sealed interface ProvidersUiState {
    data object Loading : ProvidersUiState

    data class Content(
        /** In the user's order, the active one marked. */
        val accounts: List<Account>,
        /** The Edit address dialog while it is open. */
        val edit: AddressEdit? = null,
    ) : ProvidersUiState
}

/** The address of account [id] being edited: [checking] while the server is asked, then [error] or closed. */
data class AddressEdit(
    val id: String,
    val checking: Boolean = false,
    val error: AddressError? = null,
)

enum class AddressError { InvalidAddress, CleartextNotAllowed, Unreachable, OtherServer, Unsupported, Other }

sealed interface ProvidersAction {
    /** Switches to [id]: playback pauses, the app restarts on that account. */
    data class MakeActive(
        val id: String,
    ) : ProvidersAction

    /** The accounts in their new order, after a drag or a Move up / Move down action. */
    data class Reorder(
        val ids: List<String>,
    ) : ProvidersAction

    data class Remove(
        val id: String,
        val deleteDownloads: Boolean,
    ) : ProvidersAction

    /** Syncs the active account's library now. */
    data object SyncNow : ProvidersAction

    data class StartEdit(
        val id: String,
    ) : ProvidersAction

    data class EditAddress(
        val id: String,
        val address: String,
    ) : ProvidersAction

    data object DismissEdit : ProvidersAction
}
