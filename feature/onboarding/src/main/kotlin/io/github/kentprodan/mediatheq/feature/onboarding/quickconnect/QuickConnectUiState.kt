package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

/** Quick Connect progress. Approval signs in and switches the app to the shell, so it has no state here. */
sealed interface QuickConnectUiState {
    val serverName: String

    data class Starting(
        override val serverName: String,
    ) : QuickConnectUiState

    data class Waiting(
        override val serverName: String,
        val code: String,
    ) : QuickConnectUiState

    data class Expired(
        override val serverName: String,
    ) : QuickConnectUiState

    data class Error(
        override val serverName: String,
        val unreachable: Boolean,
    ) : QuickConnectUiState
}

sealed interface QuickConnectAction {
    /** Ask the server for a new code. */
    data object Restart : QuickConnectAction
}
