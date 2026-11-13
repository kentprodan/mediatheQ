package io.github.kentprodan.mediatheq.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The session that decides between onboarding and Home on the TV (`null` while it is still being read). */
@HiltViewModel
class TvMainViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
    ) : ViewModel() {
        val session: StateFlow<SessionState?> =
            sessionRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
