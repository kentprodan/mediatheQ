package io.github.kentprodan.mediatheq.feature.tv.onboarding.login

/** User name and password on the TV (ADR 0040), for servers without Quick Connect. A failure keeps the fields. */
data class TvLoginUiState(
    val serverName: String,
    val userName: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: TvLoginError? = null,
)

enum class TvLoginError { WrongCredentials, Unreachable, Storage, Other }

sealed interface TvLoginAction {
    data class UserNameChanged(
        val userName: String,
    ) : TvLoginAction

    data class PasswordChanged(
        val password: String,
    ) : TvLoginAction

    data object Submit : TvLoginAction
}
