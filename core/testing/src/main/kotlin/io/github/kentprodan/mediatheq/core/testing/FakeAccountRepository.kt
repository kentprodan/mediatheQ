package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.ServerCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [AccountRepository] in memory; [editResult] answers address edits, records what was asked. */
class FakeAccountRepository(
    accounts: List<Account> = emptyList(),
) : AccountRepository {
    override val accounts = MutableStateFlow(accounts)
    var editResult: ServerCheck = ServerCheck.OtherServer
    val activated = mutableListOf<String>()
    val removed = mutableListOf<Pair<String, Boolean>>()
    val edited = mutableListOf<Pair<String, String>>()
    var cleanUps = 0

    override suspend fun makeActive(id: String) {
        activated += id
    }

    override suspend fun reorder(ids: List<String>) {
        accounts.update { list -> ids.mapNotNull { id -> list.firstOrNull { it.id == id } } }
    }

    override suspend fun editAddress(
        id: String,
        address: String,
    ): ServerCheck = editResult.also { edited += id to address }

    override suspend fun remove(
        id: String,
        deleteDownloads: Boolean,
    ) {
        removed += id to deleteDownloads
        accounts.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun cleanUp() {
        cleanUps++
    }
}
