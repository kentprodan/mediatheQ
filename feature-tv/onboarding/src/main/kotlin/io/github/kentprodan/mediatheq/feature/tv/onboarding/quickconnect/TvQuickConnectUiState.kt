package io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect

/**
 * Quick Connect on the TV (ADR 0040): the code in large type until the user approves it on the phone or in the web UI.
 * Approval signs in, and the app leaves onboarding by itself (the session changes).
 */
sealed interface TvQuickConnectUiState {
    val serverName: String

    /** Asking whether the server offers Quick Connect, then for a code. */
    data class Starting(
        override val serverName: String,
    ) : TvQuickConnectUiState

    data class Waiting(
        override val serverName: String,
        val code: String,
    ) : TvQuickConnectUiState

    /** Quick Connect is off on this server: only the password is left. */
    data class Disabled(
        override val serverName: String,
    ) : TvQuickConnectUiState

    data class Expired(
        override val serverName: String,
    ) : TvQuickConnectUiState

    data class Error(
        override val serverName: String,
        val unreachable: Boolean,
    ) : TvQuickConnectUiState
}

sealed interface TvQuickConnectAction {
    /** A new code. */
    data object Restart : TvQuickConnectAction
}
