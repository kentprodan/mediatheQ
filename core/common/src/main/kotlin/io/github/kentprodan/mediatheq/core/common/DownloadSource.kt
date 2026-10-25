package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/**
 * The Media3 data source downloads read from: the original file from the server, with authentication. Provided by
 * `:core:playback` (which knows the device's decoders), used by `:core:download`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadSource
