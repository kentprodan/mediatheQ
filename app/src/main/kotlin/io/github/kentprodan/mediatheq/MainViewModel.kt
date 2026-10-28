package io.github.kentprodan.mediatheq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The session that decides between onboarding and the shell (`null` while it is still being read). */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        private val userDataRepository: UserDataRepository,
        private val librarySync: LibrarySyncRepository,
        libraryPreferences: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        /** Theme, font and colors; `null` while they are read, so the first frame never shows the wrong colors. */
        val theme: StateFlow<ThemePreferences?> =
            libraryPreferences.preferences
                .map { it.theme }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val session: StateFlow<SessionState?> =
            sessionRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** A favorite or rating the server refused (it was undone), for one app-wide message. */
        val userDataError: StateFlow<MqError?> = userDataRepository.error

        fun dismissUserDataError() = userDataRepository.dismissError()

        /** A track that could not play (skipped or stopped), for one app-wide message. */
        val playbackError: StateFlow<PlaybackError?> = playback.error

        fun dismissPlaybackError() = playback.dismissError()

        /** Local network access was just granted: a sync that failed without it runs now, not at the next start. */
        fun onLocalNetworkGranted() {
            viewModelScope.launch { librarySync.sync() }
        }
    }
