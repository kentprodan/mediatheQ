package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

/**
 * Quick Connect progress. In onboarding approval signs in and switches the app to the shell; inside the app (#147) it
 * ends in [Done] and the screen goes back.
 */
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

    data class Done(
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
