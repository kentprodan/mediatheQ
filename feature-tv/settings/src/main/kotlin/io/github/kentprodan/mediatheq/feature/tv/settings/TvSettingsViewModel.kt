package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.AudioOutputRepository
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Settings on TV (#243): the HDMI output's passthrough, the streaming quality, Sync now and the account. */
@HiltViewModel
class TvSettingsViewModel
    @Inject
    constructor(
        private val outputs: AudioOutputRepository,
        private val audioPreferences: AudioPreferencesRepository,
        private val sync: LibrarySyncRepository,
        private val accounts: AccountRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        private val dialog = MutableStateFlow<TvSettingsDialog?>(null)

        val uiState: StateFlow<TvSettingsUiState> =
            combine(
                outputs.outputs,
                audioPreferences.preferences,
                sync.status,
                accounts.accounts,
                dialog,
            ) { remembered, preferences, status, list, open ->
                val active = list.firstOrNull { it.active }
                TvSettingsUiState.Content(
                    hdmi = hdmiOutput(remembered),
                    quality = preferences.unmeteredQuality,
                    sync = status,
                    serverName = active?.serverName,
                    userName = active?.userName,
                    dialog = open,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState.Loading)

        fun onAction(action: TvSettingsAction) {
            when (action) {
                TvSettingsAction.TogglePassthrough -> {
                    val id = (uiState.value as? TvSettingsUiState.Content)?.hdmi?.id ?: return
                    viewModelScope.launch {
                        outputs.updateProfile(id) { it.copy(atmosPassthrough = !it.atmosPassthrough) }
                    }
                }

                TvSettingsAction.ChooseQuality -> {
                    dialog.value = TvSettingsDialog.Quality
                }

                is TvSettingsAction.SetQuality -> {
                    dialog.value = null
                    viewModelScope.launch { audioPreferences.setStreamingQuality(metered = false, action.quality) }
                }

                // The sync runs in its own job: leaving the screen does not stop it.
                TvSettingsAction.SyncNow -> {
                    viewModelScope.launch { sync.sync() }
                }

                TvSettingsAction.AskSignOut -> {
                    dialog.value = TvSettingsDialog.SignOut
                }

                TvSettingsAction.SignOut -> {
                    dialog.value = null
                    viewModelScope.launch { signOut() }
                }

                TvSettingsAction.Dismiss -> {
                    dialog.value = null
                }
            }
        }

        /**
         * Forgets the active account as the phone does (ADR 0036): another account becomes active (a restart), or the
         * app returns to the Server screen when it was the last. Music pauses first so the saved queue is current.
         */
        private suspend fun signOut() {
            val active = accounts.accounts.first().firstOrNull { it.active } ?: return
            if (playback.state.value.isPlaying) {
                playback.togglePlayPause()
                delay(QUEUE_SAVE_WAIT)
            }
            // The TV keeps no downloads of its own (TV 1); whatever the cache holds goes with the account.
            accounts.remove(active.id, deleteDownloads = true)
        }

        private companion object {
            val QUEUE_SAVE_WAIT = 1_500.milliseconds
        }
    }

/**
 * The HDMI output the passthrough switch edits: the current output when it is HDMI, else the last HDMI output
 * remembered (the player remembers it on its first play over HDMI, #241); `null` before that.
 */
internal fun hdmiOutput(remembered: RememberedOutputs): TvHdmiOutput? {
    val hdmi = remembered.outputs.filter { it.id.kind == OutputKind.Hdmi }
    val output = hdmi.firstOrNull { it.id == remembered.current } ?: hdmi.lastOrNull() ?: return null
    return TvHdmiOutput(output.id, output.name, output.profile.atmosPassthrough)
}
