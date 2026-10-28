package io.github.kentprodan.mediatheq.core.data

/** Light or dark: following the system, fixed, or Black (dark with pure black surfaces, for OLED). */
enum class ThemeMode { System, Light, Dark, Black, SystemBlack }

/** Where the app's colors come from. */
enum class ColorSource { Iris, MaterialYou, Custom }

/** Where Now Playing's colors come from: the app theme, or the cover (as today, or more saturated). */
enum class NowPlayingColors { Off, Cover, CoverVibrant }

/** The app font; display fonts apply to headings only. Downloaded on first use, the system font until then. */
enum class AppFont {
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

/** Settings -> Interface -> Theme, font and colors (#121). */
data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Iris,
    /** Seed of [ColorSource.Custom] as opaque ARGB; Iris's seed until one is picked. */
    val customSeed: Int = IRIS_SEED,
    val nowPlayingColors: NowPlayingColors = NowPlayingColors.Cover,
    /** Cover colors tint accents only; background and surfaces stay the app theme's. */
    val ignoreSurfaces: Boolean = false,
    /** No color animation on theme or cover changes. */
    val instantColors: Boolean = false,
    val font: AppFont = AppFont.System,
) {
    companion object {
        const val IRIS_SEED = 0xFF5B45D6.toInt()
    }
}
