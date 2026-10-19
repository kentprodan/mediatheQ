package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/** `CoroutineDispatcher` for CPU work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
