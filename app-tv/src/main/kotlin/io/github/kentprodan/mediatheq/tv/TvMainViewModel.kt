package io.github.kentprodan.mediatheq.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The session that decides between onboarding and Home on the TV (`null` while it is still being read). */
@HiltViewModel
class TvMainViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        accounts: AccountRepository,
        private val gate: ProfileGate,
    ) : ViewModel() {
        val session: StateFlow<SessionState?> =
            sessionRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** "Who's watching?" (#433): due with more than one profile, or asked for from Settings. */
        val showProfiles: StateFlow<Boolean> =
            combine(gate.state, accounts.accounts.map { all -> all.count { it.signedIn } }) { state, profiles ->
                state == ProfilePicker.Opened || (state == ProfilePicker.Due && profiles > 1)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        fun openProfiles() = gate.open()

        fun profileUsed() = gate.used()

        fun closeProfiles() = gate.close()
    }
