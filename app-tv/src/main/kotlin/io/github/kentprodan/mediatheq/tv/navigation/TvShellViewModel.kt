package io.github.kentprodan.mediatheq.tv.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The TV shell (ADR 0041): what plays, for the drawer's strip; navigation itself lives in the composition. */
@HiltViewModel
class TvShellViewModel
    @Inject
    constructor(
        private val playback: PlaybackConnection,
        private val log: AppLog,
    ) : ViewModel() {
        val player: StateFlow<PlayerUiModel> = playback.state

        /** Close on Back at Home (#343): the music stops with the app; the queue stays for next time. */
        fun stopMusic() = playback.pause()

        /** A destination no TV screen registers yet: logged, never thrown (ADR 0041). */
        fun unknownDestination(key: Any) = log.warn(TAG, "No TV screen for $key")

        private companion object {
            const val TAG = "TvShell"
        }
    }
