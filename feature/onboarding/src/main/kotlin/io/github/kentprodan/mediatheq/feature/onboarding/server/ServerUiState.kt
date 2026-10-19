package io.github.kentprodan.mediatheq.feature.onboarding.server

import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey

/** The server address form. [nextKey] is a pending navigation the screen performs once, then acknowledges. */
data class ServerUiState(
    val address: String = "",
    val checking: Boolean = false,
    val error: ServerError? = null,
    val notice: LogoutReason? = null,
    val nextKey: OnboardingLoginKey? = null,
)

/** Why the address was not accepted. */
sealed interface ServerError {
    data object InvalidAddress : ServerError

    data object CleartextNotAllowed : ServerError

    data object Unreachable : ServerError

    data object NotJellyfin : ServerError

    data class Unsupported(
        val version: String,
    ) : ServerError

    data object Other : ServerError
}

sealed interface ServerAction {
    data class AddressChanged(
        val address: String,
    ) : ServerAction

    data object Connect : ServerAction

    data object NavigationHandled : ServerAction
}
