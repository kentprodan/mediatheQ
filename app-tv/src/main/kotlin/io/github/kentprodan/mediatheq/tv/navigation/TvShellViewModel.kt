package io.github.kentprodan.mediatheq.tv.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.ParentalControls
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.aboveLimit
import io.github.kentprodan.mediatheq.core.data.tvLogoSizeOf
import io.github.kentprodan.mediatheq.core.data.tvTitleOf
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.ParentalControlsKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.RemoteControl
import io.github.kentprodan.mediatheq.tv.ProfileGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The TV shell (ADR 0041): what plays, for the drawer's strip; navigation itself lives in the composition. */
@HiltViewModel
class TvShellViewModel
    @Inject
    constructor(
        private val playback: PlaybackConnection,
        private val log: AppLog,
        appMode: AppModeRepository,
        remoteControl: RemoteControl? = null,
        preferences: LibraryPreferencesRepository? = null,
        private val parental: ParentalControls? = null,
        private val videos: VideoRepository? = null,
        session: SessionRepository? = null,
        accounts: AccountRepository? = null,
        private val profileGate: ProfileGate? = null,
    ) : ViewModel() {
        /** A key waiting for the PIN (#434), with whether the last try was wrong. */
        data class PinRequest(
            val proceed: () -> Unit,
            val unlocks: Set<MediaId>,
            val wrong: Boolean = false,
        )

        private val mutablePin = MutableStateFlow<PinRequest?>(null)
        val pinRequest: StateFlow<PinRequest?> = mutablePin.asStateFlow()

        // Opened with the PIN this session: its page and its player do not ask again.
        private val unlocked = mutableSetOf<MediaId>()

        /**
         * The age gate (#434): a movie, series or player above this profile's limit, and Parental controls while a PIN
         * is set, wait for the PIN; anything else opens at once.
         * shortcut: videos started by another client (#389) or a Google TV card (#386) are not gated; gate them when a
         * child profile uses those.
         */
        fun guard(
            key: NavKey,
            proceed: () -> Unit,
        ) {
            val gate = parental ?: return proceed()
            viewModelScope.launch {
                val ids = gatedIds(key, gate)
                if (ids == null) proceed() else mutablePin.value = PinRequest(proceed, ids)
            }
        }

        /** The ids a PIN would unlock for [key], `null` when it may open. */
        private suspend fun gatedIds(
            key: NavKey,
            gate: ParentalControls,
        ): Set<MediaId>? {
            if (key == ParentalControlsKey) return if (gate.hasPin.first()) emptySet() else null
            val limit = gate.limit.first() ?: return null
            if (!gate.hasPin.first()) return null
            val repo = videos ?: return null
            val (id, ratings) =
                when (key) {
                    is MovieKey -> {
                        key.id to listOf(repo.video(key.id).first()?.officialRating)
                    }

                    is SeriesKey -> {
                        key.id to listOf(repo.series(key.id).first()?.officialRating)
                    }

                    is VideoPlayerKey -> {
                        val video = repo.video(key.id).first()
                        val series = video?.seriesId?.let { repo.series(it).first() }
                        key.id to listOf(video?.officialRating, series?.officialRating)
                    }

                    else -> {
                        return null
                    }
                }
            val seriesId = (key as? VideoPlayerKey)?.let { repo.video(it.id).first()?.seriesId }
            if (id in unlocked || seriesId in unlocked) return null
            return if (ratings.any { aboveLimit(it, limit) }) setOfNotNull(id) else null
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

        /** TV Settings -> Transitions (#444). */
        val simpleTransitions: StateFlow<Boolean> =
            (preferences?.preferences?.map { it.navigation.simpleTransitions } ?: flowOf(false))
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        /** The menu's style, TV Settings -> Display (#520). */
        val menuStyle: StateFlow<TvMenuStyle> =
            (preferences?.preferences?.map { it.navigation.tvMenuStyle } ?: flowOf(TvMenuStyle.Modal))
                .stateIn(viewModelScope, SharingStarted.Eagerly, TvMenuStyle.Modal)

        /** Who is signed in, for the menu's profile entry (#520); `null` hides it. */
        val userName: StateFlow<String?> =
            (session?.session?.map { (it as? SessionState.LoggedIn)?.userName } ?: flowOf(null))
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** The signed-in user's picture for the profile entry (#556); `null` shows the initial. */
        val userImage: StateFlow<String?> =
            (accounts?.accounts?.map { list -> list.firstOrNull { it.active }?.imageUrl } ?: flowOf(null))
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** TV Settings -> Accent from the poster (#517). */
        val posterAccent: StateFlow<Boolean> =
            (preferences?.preferences?.map { it.navigation.tvPosterAccent } ?: flowOf(false))
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        /** TV Settings -> Screens (#523, #619): how each detail page shows its title. */
        val titleStyle: StateFlow<Map<TvTitlePage, TvTitleStyle>> =
            (
                preferences?.preferences?.map { prefs ->
                    TvTitlePage.entries.associateWith(prefs.navigation::tvTitleOf)
                }
                    ?: flowOf(emptyMap())
            ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        /** TV Settings -> Screens (#578, #619): each detail page's logo size. */
        val logoSize: StateFlow<Map<TvTitlePage, TvLogoSize>> =
            (
                preferences?.preferences?.map { prefs ->
                    TvTitlePage.entries.associateWith(prefs.navigation::tvLogoSizeOf)
                }
                    ?: flowOf(emptyMap())
            ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        /** A video another client started here (#389): the shell opens the player on it. */
        val remoteVideo: Flow<MediaId> = remoteControl?.videoStarted ?: emptyFlow()

        val player: StateFlow<PlayerUiModel> = playback.state

        /** The drawer's entries for the TV's app mode (#426); music only until the mode is read. */
        val destinations: StateFlow<List<TvDestination>> =
            appMode.mode
                .map(TvDestination::shownIn)
                .stateIn(viewModelScope, SharingStarted.Eagerly, TvDestination.shownIn(AppMode.Music))

        /**
         * Close on Back at Home (#343): the music stops with the app; the queue stays for next time. The next start asks
         * Who's watching? again (#572).
         */
        fun close() {
            playback.pause()
            profileGate?.closedWithBack()
        }

        /** A destination no TV screen registers yet: logged, never thrown (ADR 0041). */
        fun unknownDestination(key: Any) = log.warn(TAG, "No TV screen for $key")

        private companion object {
            const val TAG = "TvShell"
        }
    }
