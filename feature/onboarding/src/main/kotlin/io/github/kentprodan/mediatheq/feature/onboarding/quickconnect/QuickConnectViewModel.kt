package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Starts a Quick Connect request and polls until it is approved; polling stops with the screen (viewModelScope). */
@HiltViewModel(assistedFactory = QuickConnectViewModel.Factory::class)
class QuickConnectViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: OnboardingQuickConnectKey,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow<QuickConnectUiState>(QuickConnectUiState.Starting(key.serverName))
        val uiState: StateFlow<QuickConnectUiState> = state.asStateFlow()
        private var job: Job? = null

        init {
            start()
        }

        fun onAction(action: QuickConnectAction) {
            when (action) {
                QuickConnectAction.Restart -> start()
            }
        }

        private fun start() {
            job?.cancel()
            state.value = QuickConnectUiState.Starting(key.serverName)
            job =
                viewModelScope.launch {
                    when (val started = sessionRepository.startQuickConnect(key.baseUrl)) {
                        is MqResult.Failure -> {
                            fail(started.error)
                        }

                        is MqResult.Success -> {
                            state.value = QuickConnectUiState.Waiting(key.serverName, started.value.code)
                            poll(started.value)
                        }
                    }
                }
        }

        private suspend fun poll(code: QuickConnectCode) {
            while (true) {
                delay(POLL_INTERVAL_MILLIS)
                when (val approved = sessionRepository.isQuickConnectApproved(key.baseUrl, code)) {
                    is MqResult.Success -> {
                        if (approved.value) {
                            val login = sessionRepository.loginWithQuickConnect(key.baseUrl, key.serverName, code)
                            if (login is MqResult.Failure) fail(login.error)
                            return
                        }
                    }

                    is MqResult.Failure -> {
                        when (approved.error) {
                            MqError.NotFound -> {
                                state.value = QuickConnectUiState.Expired(key.serverName)
                                return
                            }

                            // A short network blip should not end the wait; the next poll retries.
                            MqError.Network -> {
                                Unit
                            }

                            else -> {
                                return fail(approved.error)
                            }
                        }
                    }
                }
            }
        }

        private fun fail(error: MqError) {
            state.value = QuickConnectUiState.Error(key.serverName, unreachable = error == MqError.Network)
        }

        @AssistedFactory
        interface Factory {
            fun create(key: OnboardingQuickConnectKey): QuickConnectViewModel
        }

        companion object {
            /** How often the server is asked whether the code was approved. */
            const val POLL_INTERVAL_MILLIS = 5_000L
        }
    }
