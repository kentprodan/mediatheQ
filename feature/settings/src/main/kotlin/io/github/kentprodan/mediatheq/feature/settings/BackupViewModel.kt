package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.BackupCheck
import io.github.kentprodan.mediatheq.core.data.BackupRepository
import io.github.kentprodan.mediatheq.core.data.BackupRestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings -> Backup & restore (#150): writes a backup, reads one for confirmation, restores it. */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val backups: BackupRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(BackupUiState())
        val uiState: StateFlow<BackupUiState> = state.asStateFlow()

        fun onAction(action: BackupAction) {
            when (action) {
                is BackupAction.Write -> {
                    run {
                        val written = backups.write(action.uri)
                        state.update {
                            it.copy(
                                message = if (written) BackupMessage.Saved else BackupMessage.NotWritten,
                            )
                        }
                    }
                }

                is BackupAction.Pick -> {
                    run {
                        when (val check = backups.read(action.uri)) {
                            is BackupCheck.Valid -> {
                                state.update {
                                    it.copy(
                                        pending = PendingRestore(action.uri, check.summary),
                                    )
                                }
                            }

                            else -> {
                                state.update { it.copy(message = check.message()) }
                            }
                        }
                    }
                }

                BackupAction.ConfirmRestore -> {
                    val pending = state.value.pending ?: return
                    state.update { it.copy(pending = null) }
                    run {
                        val message =
                            when (val restored = backups.restore(pending.uri)) {
                                is BackupRestore.Restored -> BackupMessage.Restored(restored.toSignIn)
                                is BackupRestore.Failed -> restored.check.message()
                                BackupRestore.NotSaved -> BackupMessage.NotRestored
                            }
                        state.update { it.copy(message = message) }
                    }
                }

                BackupAction.DismissRestore -> {
                    state.update { it.copy(pending = null) }
                }

                BackupAction.DismissMessage -> {
                    state.update { it.copy(message = null) }
                }
            }
        }

        /** One file operation at a time, the buttons waiting meanwhile. */
        private fun run(block: suspend () -> Unit) {
            if (state.value.busy) return
            state.update { it.copy(busy = true, message = null) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    state.update { it.copy(busy = false) }
                }
            }
        }
    }

private fun BackupCheck.message(): BackupMessage =
    when (this) {
        BackupCheck.Unreadable -> BackupMessage.Unreadable
        else -> BackupMessage.Invalid
    }
