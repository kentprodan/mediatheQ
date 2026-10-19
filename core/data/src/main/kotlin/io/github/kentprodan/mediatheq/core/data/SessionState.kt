package io.github.kentprodan.mediatheq.core.data

/** Whether a server account is signed in. */
sealed interface SessionState {
    data class LoggedIn(
        val serverName: String,
        val userName: String,
    ) : SessionState

    /** Signed out; [reason] says why when it was not the user's choice. */
    data class LoggedOut(
        val reason: LogoutReason? = null,
    ) : SessionState
}

/** Why the app signed out on its own. */
enum class LogoutReason {
    /** The server rejected the token (401). */
    SessionExpired,

    /** The saved token could not be decrypted. */
    StorageError,
}
