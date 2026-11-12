package io.github.kentprodan.mediatheq.core.data

import kotlin.time.Instant

/**
 * Settings -> Backup & restore (#150): every setting and the accounts' addresses to a file the user picks, and back.
 * Files are content URIs from the system file picker. No token, password or DeviceId is ever written.
 */
interface BackupRepository {
    /** Writes a backup to [uri]; false when it could not be written. */
    suspend fun write(uri: String): Boolean

    /** Reads the backup at [uri] without changing anything, for the confirmation. */
    suspend fun read(uri: String): BackupCheck

    /** Replaces every setting with the backup at [uri] and adds its accounts not saved yet, signed out. */
    suspend fun restore(uri: String): BackupRestore
}

sealed interface BackupCheck {
    data class Valid(
        val summary: BackupSummary,
    ) : BackupCheck

    /** Not a mediatheQ backup, or a broken one. */
    data object Invalid : BackupCheck

    /** The file could not be opened or read. */
    data object Unreadable : BackupCheck
}

/** What a backup holds, shown before restoring it. */
data class BackupSummary(
    val created: Instant,
    val appVersion: String,
    val settingsCount: Int,
    val accounts: List<BackupAccountSummary>,
)

data class BackupAccountSummary(
    val serverName: String,
    val userName: String,
    val address: String,
)

sealed interface BackupRestore {
    /** Applied; [toSignIn] saved accounts now wait for a sign-in. */
    data class Restored(
        val toSignIn: Int,
    ) : BackupRestore

    /** Nothing changed: the file is not a valid backup ([BackupCheck.Invalid]) or could not be read. */
    data class Failed(
        val check: BackupCheck,
    ) : BackupRestore

    /** The file was valid but the settings could not be written; nothing changed. */
    data object NotSaved : BackupRestore
}
