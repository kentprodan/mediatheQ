package io.github.kentprodan.mediatheq.feature.tv.onboarding.server

import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey

/**
 * The server address on the TV (ADR 0040). [nextKey] is a pending navigation (Quick Connect, the TV's first way to
 * sign in) the screen performs once, then acknowledges.
 */
data class TvServerUiState(
    val address: String = "",
    val checking: Boolean = false,
    val error: TvServerError? = null,
    val notice: LogoutReason? = null,
    val nextKey: OnboardingQuickConnectKey? = null,
)

/** Why the address was not accepted, in plain words on screen. */
sealed interface TvServerError {
    data object InvalidAddress : TvServerError

    data object CleartextNotAllowed : TvServerError

    data object Unreachable : TvServerError

    data object NotJellyfin : TvServerError

    data class Unsupported(
        val version: String,
    ) : TvServerError

    data object Other : TvServerError
}

sealed interface TvServerAction {
    data class AddressChanged(
        val address: String,
    ) : TvServerAction

    data object Connect : TvServerAction

    data object NavigationHandled : TvServerAction
}
