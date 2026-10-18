package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/** `CoroutineDispatcher` for blocking IO (database, network). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** `CoroutineDispatcher` for CPU work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** App-wide `CoroutineScope` for work that outlives a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
