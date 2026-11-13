package io.github.kentprodan.mediatheq.feature.tv.onboarding.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Signs in with a user name and password (ADR 0040); success switches the app to Home through the session. */
@HiltViewModel(assistedFactory = TvLoginViewModel.Factory::class)
class TvLoginViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: OnboardingLoginKey,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(TvLoginUiState(serverName = key.serverName))
        val uiState: StateFlow<TvLoginUiState> = state.asStateFlow()

        fun onAction(action: TvLoginAction) {
            when (action) {
                is TvLoginAction.UserNameChanged -> state.update { it.copy(userName = action.userName, error = null) }
                is TvLoginAction.PasswordChanged -> state.update { it.copy(password = action.password, error = null) }
                TvLoginAction.Submit -> submit()
            }
        }

        private fun submit() {
            val form = state.value
            if (form.submitting || form.userName.isBlank()) return
            state.update { it.copy(submitting = true, error = null) }
            viewModelScope.launch {
                val result = sessionRepository.login(key.baseUrl, key.serverName, form.userName.trim(), form.password)
                state.update {
                    it.copy(
                        submitting = false,
                        error = (result as? MqResult.Failure)?.error?.toLoginError(),
                    )
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(key: OnboardingLoginKey): TvLoginViewModel
        }
    }

private fun MqError.toLoginError(): TvLoginError =
    when (this) {
        MqError.Unauthorized -> TvLoginError.WrongCredentials
        MqError.Network -> TvLoginError.Unreachable
        MqError.Storage -> TvLoginError.Storage
        else -> TvLoginError.Other
    }
