package io.github.kentprodan.mediatheq.feature.onboarding.login

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
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = LoginViewModel.Factory::class)
class LoginViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: OnboardingLoginKey,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(LoginUiState(serverName = key.serverName))
        val uiState: StateFlow<LoginUiState> = state.asStateFlow()

        init {
            viewModelScope.launch {
                val enabled = sessionRepository.isQuickConnectEnabled(key.baseUrl)
                val availability =
                    if (enabled == MqResult.Success(true)) {
                        QuickConnectAvailability.Available
                    } else {
                        QuickConnectAvailability.Disabled
                    }
                state.update { it.copy(quickConnect = availability) }
            }
        }

        fun onAction(action: LoginAction) {
            when (action) {
                is LoginAction.UserNameChanged -> {
                    state.update { it.copy(userName = action.userName, error = null) }
                }

                is LoginAction.PasswordChanged -> {
                    state.update { it.copy(password = action.password, error = null) }
                }

                LoginAction.Submit -> {
                    submit()
                }

                LoginAction.OpenQuickConnect -> {
                    state.update { it.copy(nextKey = OnboardingQuickConnectKey(key.baseUrl, key.serverName)) }
                }

                LoginAction.NavigationHandled -> {
                    state.update { it.copy(nextKey = null) }
                }
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
            fun create(key: OnboardingLoginKey): LoginViewModel
        }
    }

private fun MqError.toLoginError(): LoginError =
    when (this) {
        MqError.Unauthorized -> LoginError.WrongCredentials
        MqError.Network -> LoginError.Unreachable
        MqError.Storage -> LoginError.Storage
        else -> LoginError.Other
    }
