package io.github.kentprodan.mediatheq.feature.onboarding.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(ServerUiState())

        val uiState: StateFlow<ServerUiState> = form.asStateFlow()

        init {
            viewModelScope.launch {
                sessionRepository.session.collect { session ->
                    form.update { it.copy(notice = (session as? SessionState.LoggedOut)?.reason) }
                }
            }
        }

        fun onAction(action: ServerAction) {
            when (action) {
                is ServerAction.AddressChanged -> form.update { it.copy(address = action.address, error = null) }
                ServerAction.Connect -> connect()
                ServerAction.NavigationHandled -> form.update { it.copy(nextKey = null) }
            }
        }

        private fun connect() {
            if (form.value.checking) return
            form.update { it.copy(checking = true, error = null) }
            viewModelScope.launch {
                val check = sessionRepository.checkServer(form.value.address)
                form.update {
                    when (check) {
                        is ServerCheck.Supported -> {
                            it.copy(checking = false, nextKey = OnboardingLoginKey(check.baseUrl, check.info.name))
                        }

                        else -> {
                            it.copy(checking = false, error = check.toError())
                        }
                    }
                }
            }
        }
    }

private fun ServerCheck.toError(): ServerError =
    when (this) {
        is ServerCheck.Supported -> {
            error("not an error")
        }

        is ServerCheck.Unsupported -> {
            ServerError.Unsupported(info.version)
        }

        ServerCheck.InvalidAddress -> {
            ServerError.InvalidAddress
        }

        ServerCheck.CleartextNotAllowed -> {
            ServerError.CleartextNotAllowed
        }

        is ServerCheck.Failed -> {
            when (error) {
                MqError.Network -> ServerError.Unreachable
                is MqError.Unknown -> ServerError.NotJellyfin
                else -> ServerError.Other
            }
        }
    }
