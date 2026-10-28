package io.github.kentprodan.mediatheq.core.designsystem.theme

import com.materialkolor.hct.Hct

/** Seeds offered for a custom color (#121), Iris first; any other comes from a hue ([seedOfHue]). */
val MqSeedPresets: List<Int> =
    listOf(
        MqThemeOptions.IRIS_SEED,
        0xFF1E6FD9.toInt(),
        0xFF00897B.toInt(),
        0xFF3F9142.toInt(),
        0xFFE0A100.toInt(),
        0xFFE8590C.toInt(),
        0xFFD6336C.toInt(),
        0xFF7A5C45.toInt(),
    )

/** The hue (0-360) of [seed]. */
fun seedHue(seed: Int): Float = Hct.fromInt(seed).hue.toFloat()

/** A vivid mid-tone seed of [hue] (0-360); the scheme generator sets the tones of every role from it. */
fun seedOfHue(hue: Float): Int = Hct.from(hue.toDouble(), SEED_CHROMA, SEED_TONE).toInt()

private const val SEED_CHROMA = 60.0
private const val SEED_TONE = 50.0
