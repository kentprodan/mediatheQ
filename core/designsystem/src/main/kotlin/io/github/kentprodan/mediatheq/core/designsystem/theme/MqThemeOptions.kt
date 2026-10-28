package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Light or dark: following the system, fixed, or Black (dark with pure black background and surfaces). */
enum class MqThemeMode { System, Light, Dark, Black, SystemBlack }

/** Where the app's colors come from. */
enum class MqColorSource { Iris, MaterialYou, Custom }

/** Where the player's colors come from: the app theme, or the cover (`SchemeContent`, or `SchemeVibrant`). */
enum class MqPlayerColors { Off, Cover, CoverVibrant }

/** The app font (ADR 0026): whole fonts, a heading/body pair, or display fonts for headings only. */
enum class MqFont {
    System,
    Inter,
    Lato,
    Nunito,
    Roboto,
    RobotoSlab,
    LexendDeca,
    BitterRubik,
    BebasNeue,
    CastoroTitling,
}

/** What [MediatheqTheme] draws with (#121); `:app` maps the stored settings to it. */
@Immutable
data class MqThemeOptions(
    val mode: MqThemeMode = MqThemeMode.System,
    val colorSource: MqColorSource = MqColorSource.Iris,
    /** Seed of [MqColorSource.Custom], opaque ARGB. */
    val customSeed: Int = IRIS_SEED,
    val playerColors: MqPlayerColors = MqPlayerColors.Cover,
    /** Cover colors tint accents only; background and surfaces stay the app theme's. */
    val ignoreSurfaces: Boolean = false,
    /** No color animation on theme or cover changes. */
    val instantColors: Boolean = false,
    val font: MqFont = MqFont.System,
) {
    /** Whether the theme is dark when the system is [systemDark]. */
    fun isDark(systemDark: Boolean): Boolean =
        when (mode) {
            MqThemeMode.Light -> false
            MqThemeMode.Dark, MqThemeMode.Black -> true
            MqThemeMode.System, MqThemeMode.SystemBlack -> systemDark
        }

    /** Whether background and surfaces are pure black when the system is [systemDark]. */
    fun isBlack(systemDark: Boolean): Boolean =
        mode == MqThemeMode.Black || (mode == MqThemeMode.SystemBlack && systemDark)

    companion object {
        /** Iris's seed (ADR 0012). */
        const val IRIS_SEED = 0xFF5B45D6.toInt()
    }
}

/** The theme in effect, for parts with their own colors (the player). */
@Immutable
data class MqThemeState(
    val options: MqThemeOptions = MqThemeOptions(),
    val isDark: Boolean = false,
    val isBlack: Boolean = false,
)

/** Provided by [MediatheqTheme]. */
val LocalMqTheme = staticCompositionLocalOf { MqThemeState() }
