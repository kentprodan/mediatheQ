package io.github.kentprodan.mediatheq.core.common

import kotlinx.coroutines.flow.Flow

/** The active account's server addresses (#382), the preferred one (the account's address) first. */
interface ServerAddressSource {
    val addresses: Flow<List<String>>
}
