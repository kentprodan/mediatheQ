package io.github.kentprodan.mediatheq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import io.github.kentprodan.mediatheq.core.playback.slowStarts
import io.github.kentprodan.mediatheq.core.ui.MediaListStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
        private val libraryPreferences: LibraryPreferencesRepository,
        private val playback: PlaybackConnection,
        libraryRepository: LibraryRepository,
        downloads: DownloadRepository,
        private val appModeRepository: AppModeRepository,
    ) : ViewModel() {
        /**
         * Theme, font, colors and corners (#137, read with the theme so the first frame never changes shape); `null`
         * while they are read, so the first frame never shows the wrong colors.
         */
        val theme: StateFlow<MqThemeOptions?> =
            libraryPreferences.preferences
                .map { it.theme.toThemeOptions(it.advanced.corners) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Advanced settings (#137): keep screen on, orientation, status bar. */
        val advanced: StateFlow<AdvancedPreferences> =
            libraryPreferences.preferences
                .map { it.advanced }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdvancedPreferences())

        /** Navigation and media start (#122): the shell's entries and styles. */
        val navigation: StateFlow<NavigationPreferences> =
            libraryPreferences.preferences
                .map { it.navigation }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationPreferences())

        /** How every media list looks (#123): its settings, the rating mode and what is downloaded. */
        val mediaListStyle: StateFlow<MediaListStyle> =
            combine(
                libraryPreferences.preferences,
                downloads.completedIds,
                libraryRepository.fullyDownloaded,
            ) { preferences, tracks, collections -> preferences.toMediaListStyle(tracks + collections) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaListStyle())

        val session: StateFlow<SessionState?> =
            sessionRepository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /**
         * The app mode (ADR 0051): the shell's tabs, and in Video mode no Now Playing overlay or mini player; `null` while
         * it is read, so the first frame never shows the other mode's shell.
         */
        val appMode: StateFlow<AppMode?> =
            appModeRepository.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        // Set once this process was signed out (onboarding showed): an install upgraded while signed in is never asked.
        private val signedOutSeen = MutableStateFlow(false)

        /** The app mode step: after a sign-in here, until a mode is stored, and only on a server with video. */
        val asksAppMode: StateFlow<Boolean> =
            combine(sessionRepository.session, appModeRepository.chosen, signedOutSeen) { session, chosen, seen ->
                appModeRepository.videoAvailable && seen && !chosen && session is SessionState.LoggedIn
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /** A favorite or rating the server refused (it was undone), for one app-wide message. */
        val userDataError: StateFlow<MqError?> = userDataRepository.error

        fun dismissUserDataError() = userDataRepository.dismissError()

        /** A track that could not play (skipped or stopped), for one app-wide message. */
        val playbackError: StateFlow<PlaybackError?> = playback.error

        fun dismissPlaybackError() = playback.dismissError()

        private val slowStartItem = MutableStateFlow<NowPlayingItem?>(null)

        /** A track slow to start (#122), for one app-wide message; never while the setting is off. */
        val slowStart: StateFlow<NowPlayingItem?> = slowStartItem

        fun dismissSlowStart() {
            slowStartItem.value = null
        }

        init {
            // Always on, not only while the shell collects asksAppMode: onboarding is what is shown while signed out.
            viewModelScope.launch {
                sessionRepository.session.first { it is SessionState.LoggedOut }
                signedOutSeen.value = true
            }
            // Video mode has no music controls on screen (ADR 0051): music playing when it is picked pauses.
            viewModelScope.launch {
                appModeRepository.mode.drop(1).collect { mode ->
                    if (!mode.music && playback.state.value.isPlaying) playback.togglePlayPause()
                }
            }
            viewModelScope.launch {
                playback.state.slowStarts().collect { item ->
                    if (libraryPreferences.preferences
                            .first()
                            .navigation.slowStartMessages
                    ) {
                        slowStartItem.value = item
                    }
                }
            }
        }

        /** Local network access was just granted: a sync that failed without it runs now, not at the next start. */
        fun onLocalNetworkGranted() {
            viewModelScope.launch { librarySync.sync() }
        }
    }
