package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/** `CoroutineDispatcher` for blocking IO (database, network). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
