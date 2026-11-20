package io.github.kentprodan.mediatheq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.DroppedPlaylistEdits
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.ParentalControls
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.needsPin
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaylistConflict
import io.github.kentprodan.mediatheq.core.model.PlaylistResolution
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlaybackError
import io.github.kentprodan.mediatheq.core.playback.slowStarts
import io.github.kentprodan.mediatheq.core.ui.MediaListStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val parental: ParentalControls? = null,
        private val videos: VideoRepository? = null,
        private val playlists: PlaylistRepository? = null,
    ) : ViewModel() {
        /** A page or player waiting for the PIN (#443), with whether the last try was wrong. */
        data class PinRequest(
            val proceed: () -> Unit,
            val unlocks: MediaId,
            val wrong: Boolean = false,
        )

        private val mutablePin = MutableStateFlow<PinRequest?>(null)
        val pinRequest: StateFlow<PinRequest?> = mutablePin.asStateFlow()

        // Opened with the PIN this session: its page and its player do not ask again.
        private val unlocked = mutableSetOf<MediaId>()

        /**
         * The age gate (#443, like the TV's #434): a movie, series or player above this profile's limit waits for the
         * PIN; anything else opens at once.
         * shortcut: a video sent from another client (#389) opens without the gate.
         */
        fun guard(
            key: NavKey,
            proceed: () -> Unit,
        ) {
            val gate = parental
            val repo = videos
            val (id, series) =
                when (key) {
                    is MovieKey -> key.id to false
                    is SeriesKey -> key.id to true
                    is VideoPlayerKey -> key.id to false
                    else -> return proceed()
                }
            if (gate == null || repo == null || id in unlocked) return proceed()
            viewModelScope.launch {
                val seriesId = if (series) null else repo.video(id).first()?.seriesId
                when {
                    seriesId != null && seriesId in unlocked -> proceed()
                    gate.needsPin(repo, id, series) -> mutablePin.value = PinRequest(proceed, id)
                    else -> proceed()
                }
            }
        }

        fun submitPin(pin: String) {
            val request = mutablePin.value ?: return
            viewModelScope.launch {
                if (parental?.checkPin(pin) != false) {
                    unlocked += request.unlocks
                    mutablePin.value = null
                    request.proceed()
                } else {
                    mutablePin.value = request.copy(wrong = true)
                }
            }
        }

        fun pinEdited() {
            mutablePin.value = mutablePin.value?.copy(wrong = false)
        }

        fun cancelPin() {
            mutablePin.value = null
        }

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

        // The conflict list Later hid: shown again when the next connection publishes a new list (#417).
        private val hiddenConflicts = MutableStateFlow<List<PlaylistConflict>?>(null)

        /** A playlist edited offline that also changed on the server (#417): one app-wide question at a time. */
        val playlistConflict: StateFlow<PlaylistConflict?> =
            combine(playlists?.conflicts ?: MutableStateFlow(emptyList()), hiddenConflicts) { all, hidden ->
                if (all === hidden) null else all.firstOrNull()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Later (and Back): asked again at the next connection; the edits keep waiting. */
        fun hidePlaylistConflict() {
            hiddenConflicts.value = playlists?.conflicts?.value
        }

        private val answerFailed = MutableStateFlow(false)

        /** An answer that could not reach the server: the question closes, comes back at the next connection. */
        val playlistAnswerFailed: StateFlow<Boolean> = answerFailed.asStateFlow()

        fun dismissPlaylistAnswerFailed() {
            answerFailed.value = false
        }

        /** Merge, Replace or Skip; when the server cannot take it, the question waits for the next connection. */
        fun resolvePlaylistConflict(
            id: MediaId,
            resolution: PlaylistResolution,
        ) {
            viewModelScope.launch {
                if (playlists?.resolve(id, resolution) is MqResult.Failure) {
                    hidePlaylistConflict()
                    answerFailed.value = true
                }
            }
        }

        /** Offline playlist edits the server refused, one app-wide message each (#417). */
        val droppedPlaylistEdits: StateFlow<List<DroppedPlaylistEdits>> =
            playlists?.droppedEdits ?: MutableStateFlow(emptyList())

        fun dismissDroppedPlaylistEdits() {
            playlists?.acknowledgeDroppedEdits()
        }

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
