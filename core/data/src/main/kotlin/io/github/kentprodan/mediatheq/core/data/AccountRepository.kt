package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/**
 * The saved media server accounts (#147, ADR 0036): one is active, each keeps its own library, queue and downloads.
 * Signing in (also to a new account) goes through [SessionRepository].
 */
interface AccountRepository {
    /** Every account in the user's order; empty when signed out or unreadable. */
    val accounts: Flow<List<Account>>

    /** Makes [id] the active account: the app restarts on its library and queue. */
    suspend fun makeActive(id: String)

    /** Saves the order of [ids]. */
    suspend fun reorder(ids: List<String>)

    /**
     * Checks [address] and, when it is the same server, saves it as the account's address (library and token kept).
     * [ServerCheck.Supported] when saved; [ServerCheck.OtherServer] when another server answers.
     */
    suspend fun editAddress(
        id: String,
        address: String,
    ): ServerCheck

    /**
     * Signs out of [id] and forgets it with its library, queue and waiting changes; [deleteDownloads] also deletes its
     * downloads unless another account uses the same server. The active one: another becomes active (restart), or the
     * app returns to onboarding when it was the last.
     */
    suspend fun remove(
        id: String,
        deleteDownloads: Boolean,
    )

    /** Deletes the databases no account owns (a removal cut short); call once at start. */
    suspend fun cleanUp()
}

/** One saved account; [id] is stable (its database's name). */
data class Account(
    val id: String,
    val serverName: String,
    val userName: String,
    val baseUrl: String,
    val active: Boolean,
)
