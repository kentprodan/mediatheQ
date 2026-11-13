package io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect

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

/**
 * The TV's first way to sign in (ADR 0040): asks whether the server offers Quick Connect, shows a code and polls until
 * it is approved; polling stops with the screen (viewModelScope). The same protocol as the phone's.
 */
@HiltViewModel(assistedFactory = TvQuickConnectViewModel.Factory::class)
class TvQuickConnectViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: OnboardingQuickConnectKey,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow<TvQuickConnectUiState>(TvQuickConnectUiState.Starting(key.serverName))
        val uiState: StateFlow<TvQuickConnectUiState> = state.asStateFlow()

        private var job: Job? = null

        init {
            start()
        }

        fun onAction(action: TvQuickConnectAction) {
            when (action) {
                TvQuickConnectAction.Restart -> start()
            }
        }

        private fun start() {
            job?.cancel()
            state.value = TvQuickConnectUiState.Starting(key.serverName)
            job =
                viewModelScope.launch {
                    when (val enabled = sessionRepository.isQuickConnectEnabled(key.baseUrl)) {
                        is MqResult.Failure -> {
                            return@launch fail(enabled.error)
                        }

                        is MqResult.Success -> {
                            if (!enabled.value) {
                                state.value = TvQuickConnectUiState.Disabled(key.serverName)
                                return@launch
                            }
                        }
                    }
                    when (val started = sessionRepository.startQuickConnect(key.baseUrl)) {
                        is MqResult.Failure -> {
                            fail(started.error)
                        }

                        is MqResult.Success -> {
                            state.value = TvQuickConnectUiState.Waiting(key.serverName, started.value.code)
                            poll(started.value)
                        }
                    }
                }
        }

        private suspend fun poll(code: QuickConnectCode) {
            repeat(MAX_POLLS) {
                delay(POLL_INTERVAL_MILLIS)
                when (val approved = sessionRepository.isQuickConnectApproved(key.baseUrl, code)) {
                    is MqResult.Success -> {
                        if (approved.value) {
                            // Success switches the app to Home through the session; only a failure stays here.
                            val login = sessionRepository.loginWithQuickConnect(key.baseUrl, key.serverName, code)
                            if (login is MqResult.Failure) fail(login.error)
                            return
                        }
                    }

                    is MqResult.Failure -> {
                        when (approved.error) {
                            MqError.NotFound -> return run {
                                state.value =
                                    TvQuickConnectUiState.Expired(
                                        key.serverName,
                                    )
                            }

                            // A short network blip should not end the wait; the next poll retries.
                            MqError.Network -> Unit

                            else -> return fail(approved.error)
                        }
                    }
                }
            }
            // The server forgets codes after a few minutes; stop asking.
            state.value = TvQuickConnectUiState.Expired(key.serverName)
        }

        private fun fail(error: MqError) {
            state.value = TvQuickConnectUiState.Error(key.serverName, unreachable = error == MqError.Network)
        }

        @AssistedFactory
        interface Factory {
            fun create(key: OnboardingQuickConnectKey): TvQuickConnectViewModel
        }

        companion object {
            /** How often the server is asked whether the code was approved. */
            const val POLL_INTERVAL_MILLIS = 5_000L

            /** Polls before giving up: 10 minutes. */
            const val MAX_POLLS = 120
        }
    }
