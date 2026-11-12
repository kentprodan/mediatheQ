package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.BackupSummary

/** Settings -> Backup & restore (#150). */
data class BackupUiState(
    /** Writing, reading or restoring: the buttons wait. */
    val busy: Boolean = false,
    /** The backup picked for a restore, shown for confirmation. */
    val pending: PendingRestore? = null,
    /** How the last backup or restore went, until dismissed. */
    val message: BackupMessage? = null,
)

data class PendingRestore(
    val uri: String,
    val summary: BackupSummary,
)

sealed interface BackupMessage {
    data object Saved : BackupMessage

    data object NotWritten : BackupMessage

    /** Restored; [toSignIn] accounts wait for a sign-in in Manage media providers. */
    data class Restored(
        val toSignIn: Int,
    ) : BackupMessage

    data object Unreadable : BackupMessage

    data object Invalid : BackupMessage

    /** The settings could not be saved; nothing changed. */
    data object NotRestored : BackupMessage
}

sealed interface BackupAction {
    /** The file the picker created for a backup. */
    data class Write(
        val uri: String,
    ) : BackupAction

    /** The backup the picker opened: read for its summary. */
    data class Pick(
        val uri: String,
    ) : BackupAction

    data object ConfirmRestore : BackupAction

    data object DismissRestore : BackupAction

    data object DismissMessage : BackupAction
}
