package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ServerAddressSource
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** The active account's addresses for the network's failover (#382): its address, then its other addresses. */
class AccountAddresses
    @Inject
    constructor(
        store: AccountStore,
    ) : ServerAddressSource {
        override val addresses: Flow<List<String>> = store.activeAddresses.distinctUntilChanged()
    }
