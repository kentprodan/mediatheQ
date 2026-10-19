package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.flow.first

/** The signed-in account as a [SignedInServer], `null` when signed out or unreadable. */
internal suspend fun AccountStore.signedInServer(): SignedInServer? =
    (account.first() as? MqResult.Success)?.value?.let { signedInServer(it) }

internal suspend fun AccountStore.signedInServer(account: StoredAccount) =
    SignedInServer(ServerConnection(account.baseUrl, deviceId(), account.accessToken), account.serverId, account.userId)
