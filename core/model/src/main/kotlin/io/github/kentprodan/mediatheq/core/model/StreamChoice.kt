package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Duration

/**
 * What a video stream request carries besides the profile (ADR 0050): where it starts (the server begins its
 * conversion there) and the audio and subtitle streams chosen (`null` = the server's defaults; chosen in #274).
 */
data class StreamChoice(
    val startPosition: Duration = Duration.ZERO,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)
