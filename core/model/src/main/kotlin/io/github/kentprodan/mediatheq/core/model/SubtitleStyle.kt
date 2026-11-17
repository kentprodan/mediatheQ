package io.github.kentprodan.mediatheq.core.model

/**
 * How subtitles look (#429): each part follows the system's caption style unless set ([System]). Shared by the TV
 * player and, later, the phone's.
 */
data class SubtitleStyle(
    val size: Size = Size.System,
    val background: Background = Background.System,
    val color: Color = Color.System,
) {
    /** Text size as a share of the picture's height; [System] keeps the caption setting. */
    enum class Size(
        val fraction: Float?,
    ) {
        System(null),
        Small(0.045f),
        Normal(0.0533f),
        Large(0.07f),
    }

    enum class Background { System, None, Shadow, Box }

    /** ARGB, `null` for [System]. */
    enum class Color(
        val argb: Int?,
    ) {
        System(null),
        White(0xFFFFFFFF.toInt()),
        Yellow(0xFFFFEB3B.toInt()),
    }
}
