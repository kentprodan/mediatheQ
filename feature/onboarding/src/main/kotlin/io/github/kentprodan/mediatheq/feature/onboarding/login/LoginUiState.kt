package io.github.kentprodan.mediatheq.feature.onboarding.login

import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey

/** The sign-in form. A successful sign-in switches the whole app to the shell, so it has no state here. */
data class LoginUiState(
    val serverName: String,
    val userName: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: LoginError? = null,
    val quickConnect: QuickConnectAvailability = QuickConnectAvailability.Checking,
    val nextKey: OnboardingQuickConnectKey? = null,
)

enum class LoginError { WrongCredentials, Unreachable, Storage, Other }

enum class QuickConnectAvailability { Checking, Available, Disabled }

sealed interface LoginAction {
    data class UserNameChanged(
        val userName: String,
    ) : LoginAction

    data class PasswordChanged(
        val password: String,
    ) : LoginAction

    data object Submit : LoginAction

    data object OpenQuickConnect : LoginAction

    data object NavigationHandled : LoginAction
}
