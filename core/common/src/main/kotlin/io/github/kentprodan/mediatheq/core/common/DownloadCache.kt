package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/**
 * The Media3 cache that holds downloads (never evicted). Provided by `:core:download`, read by the player in
 * `:core:playback`; the qualifier lets both meet in the Hilt graph without a module edge.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache
