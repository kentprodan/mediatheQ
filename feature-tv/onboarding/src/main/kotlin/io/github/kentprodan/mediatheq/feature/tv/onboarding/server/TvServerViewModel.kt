package io.github.kentprodan.mediatheq.feature.tv.onboarding.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The server address on the TV (ADR 0040), written against the same repository as the phone's: checks the address and
 * goes on to Quick Connect. Signing out removes the account and its address, so there is none to fill in (as on the
 * phone).
 */
@HiltViewModel
class TvServerViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(TvServerUiState())

        val uiState: StateFlow<TvServerUiState> = form.asStateFlow()

        init {
            viewModelScope.launch {
                sessionRepository.session.collect { session ->
                    form.update { it.copy(notice = (session as? SessionState.LoggedOut)?.reason) }
                }
            }
        }

        fun onAction(action: TvServerAction) {
            when (action) {
                is TvServerAction.AddressChanged -> form.update { it.copy(address = action.address, error = null) }
                TvServerAction.Connect -> connect()
                TvServerAction.NavigationHandled -> form.update { it.copy(nextKey = null) }
            }
        }

        private fun connect() {
            if (form.value.checking) return
            form.update { it.copy(checking = true, error = null) }
            viewModelScope.launch {
                val check = sessionRepository.checkServer(form.value.address)
                form.update {
                    if (check is ServerCheck.Supported) {
                        it.copy(checking = false, nextKey = OnboardingQuickConnectKey(check.baseUrl, check.info.name))
                    } else {
                        it.copy(checking = false, error = check.toError())
                    }
                }
            }
        }
    }

private fun ServerCheck.toError(): TvServerError =
    when (this) {
        is ServerCheck.Supported -> {
            error("not an error")
        }

        is ServerCheck.Unsupported -> {
            TvServerError.Unsupported(info.version)
        }

        ServerCheck.InvalidAddress -> {
            TvServerError.InvalidAddress
        }

        ServerCheck.CleartextNotAllowed -> {
            TvServerError.CleartextNotAllowed
        }

        // Only when editing an account's address, which the TV does not do.
        ServerCheck.OtherServer -> {
            TvServerError.Other
        }

        is ServerCheck.Failed -> {
            when (error) {
                MqError.Network -> TvServerError.Unreachable
                is MqError.Unknown -> TvServerError.NotJellyfin
                else -> TvServerError.Other
            }
        }
    }
