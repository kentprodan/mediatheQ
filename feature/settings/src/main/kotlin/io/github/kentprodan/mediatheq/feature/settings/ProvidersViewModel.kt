package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.ServerCheck
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

/** Settings -> Manage media providers (#147): the saved accounts, switching, editing and removing them. */
@HiltViewModel
class ProvidersViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
        private val sync: LibrarySyncRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        private val edit = MutableStateFlow<AddressEdit?>(null)

        val uiState: StateFlow<ProvidersUiState> =
            combine(accounts.accounts, edit) { list, edit -> ProvidersUiState.Content(list, edit) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProvidersUiState.Loading)

        fun onAction(action: ProvidersAction) {
            viewModelScope.launch {
                when (action) {
                    is ProvidersAction.MakeActive -> {
                        pauseForRestart()
                        accounts.makeActive(action.id)
                    }

                    is ProvidersAction.Reorder -> {
                        accounts.reorder(action.ids)
                    }

                    is ProvidersAction.Remove -> {
                        // Removing the active account restarts on another one, like a switch.
                        if (accounts.accounts.first().any { it.id == action.id && it.active }) pauseForRestart()
                        accounts.remove(action.id, action.deleteDownloads)
                    }

                    ProvidersAction.SyncNow -> {
                        sync.sync()
                    }

                    is ProvidersAction.StartEdit -> {
                        edit.value = AddressEdit(action.id)
                    }

                    is ProvidersAction.EditAddress -> {
                        edit.value = AddressEdit(action.id, checking = true)
                        val check = accounts.editAddress(action.id, action.address)
                        edit.value = check.toAddressError()?.let { AddressEdit(action.id, error = it) }
                    }

                    ProvidersAction.DismissEdit -> {
                        edit.value = null
                    }
                }
            }
        }

        /** Pauses, and waits until the queue's place is saved (1 s after a pause), so the restart loses no position. */
        private suspend fun pauseForRestart() {
            if (!playback.state.value.isPlaying) return
            playback.togglePlayPause()
            delay(QUEUE_SAVE_WAIT)
        }

        private companion object {
            val QUEUE_SAVE_WAIT = 1_500.milliseconds
        }
    }

/** `null` when the address was saved. */
private fun ServerCheck.toAddressError(): AddressError? =
    when (this) {
        is ServerCheck.Supported -> null
        is ServerCheck.Unsupported -> AddressError.Unsupported
        ServerCheck.InvalidAddress -> AddressError.InvalidAddress
        ServerCheck.CleartextNotAllowed -> AddressError.CleartextNotAllowed
        ServerCheck.OtherServer -> AddressError.OtherServer
        is ServerCheck.Failed -> if (error == MqError.Network) AddressError.Unreachable else AddressError.Other
    }
