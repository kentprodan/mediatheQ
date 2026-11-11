package io.github.kentprodan.mediatheq.core.common

import javax.inject.Qualifier

/**
 * The Media3 cache that holds downloads (never evicted). Provided by `:core:download`, read by the player in
 * `:core:playback`; the qualifier lets both meet in the Hilt graph without a module edge.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

/**
 * The folder that keeps the covers of downloaded albums (#154), next to the downloads so both always move together.
 * Provided by `:core:download`, used by `DownloadedCovers` in `:core:data`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCoverDir

/**
 * Content metadata of a download in the [DownloadCache]: the bitrate in kb/s when the file is the server's conversion
 * (#68, #144), 0 for the original; 1, written before #144, means Opus 256. Written by the download source, read for
 * the Downloads tab and Now Playing.
 */
const val CONVERTED_DOWNLOAD = "mediatheq.converted"
