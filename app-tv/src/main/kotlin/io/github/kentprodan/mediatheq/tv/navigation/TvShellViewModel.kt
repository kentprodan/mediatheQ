package io.github.kentprodan.mediatheq.tv.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.RemoteControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    ) : ViewModel() {
        /** A video another client started here (#389): the shell opens the player on it. */
        val remoteVideo: Flow<MediaId> = remoteControl?.videoStarted ?: emptyFlow()

        val player: StateFlow<PlayerUiModel> = playback.state

        /** The drawer's entries for the TV's app mode (#426); music only until the mode is read. */
        val destinations: StateFlow<List<TvDestination>> =
            appMode.mode
                .map(TvDestination::shownIn)
                .stateIn(viewModelScope, SharingStarted.Eagerly, TvDestination.shownIn(AppMode.Music))

        /** Close on Back at Home (#343): the music stops with the app; the queue stays for next time. */
        fun stopMusic() = playback.pause()

        /** A destination no TV screen registers yet: logged, never thrown (ADR 0041). */
        fun unknownDestination(key: Any) = log.warn(TAG, "No TV screen for $key")

        private companion object {
            const val TAG = "TvShell"
        }
    }
