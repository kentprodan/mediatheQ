package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/** App-wide `CoroutineScope` for work that outlives a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
