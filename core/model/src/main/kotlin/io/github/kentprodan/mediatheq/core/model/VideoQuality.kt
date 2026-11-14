package io.github.kentprodan.mediatheq.core.model

/**
 * The quality videos stream at (ADR 0050): the bitrate cap and the tallest picture; [Original] has neither. Defaults
 * Original on unmetered networks and [P480] on metered ones; Settings -> Video sets them (#276).
 */
enum class VideoQuality(
    /** Bits per second; `null` = no cap. */
    val bitrate: Int?,
    val maxHeight: Int?,
) {
    Original(null, null),
    P1080(10_000_000, 1080),
    P720(4_000_000, 720),
    P480(1_500_000, 480),
    P360(800_000, 360),
}
