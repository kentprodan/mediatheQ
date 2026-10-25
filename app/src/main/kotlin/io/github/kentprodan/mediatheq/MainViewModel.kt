package io.github.kentprodan.mediatheq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The session that decides between onboarding and the shell (`null` while it is still being read), and whether the
 * device is online (the shell shows the offline state otherwise).
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val userDataRepository: UserDataRepository,
        networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        val session: StateFlow<SessionState?> =
            sessionRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** A favorite or rating the server refused (it was undone), for one app-wide message. */
        val userDataError: StateFlow<MqError?> = userDataRepository.error

        fun dismissUserDataError() = userDataRepository.dismissError()

        // Starts online: a wrong "offline" flash would hide the tabs for nothing.
        val online: StateFlow<Boolean> =
            networkMonitor.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        fun logout() {
            viewModelScope.launch { sessionRepository.logout() }
        }
    }
