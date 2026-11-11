package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/**
 * The file name of the Room database this process opened: the active account's (#147, ADR 0036). It doubles as the
 * account's id. Provided by `:core:database`, read by the account store in `:core:datastore`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveDatabase

/** The database of the first account, and of the account migrated from the single-account app. */
const val DEFAULT_DATABASE = "mediatheq.db"
