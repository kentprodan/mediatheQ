package io.github.kentprodan.mediatheq.core.data

/** When the screen stays on (#137). */
enum class KeepScreenOn {
    Off,

    /** While the full Now Playing screen is open. */
    NowPlaying,
    Always,
}

/** The app's screen orientation (#137); System follows the phone's rotation setting. */
enum class ScreenOrientation { System, Portrait, Landscape }

/** How round the theme's corners are (#137): its shape tokens scaled, never per-screen values. */
enum class CornerStyle { Small, Normal, Large }

/** Settings -> Interface -> Advanced settings (#137). */
data class AdvancedPreferences(
    val keepScreenOn: KeepScreenOn = KeepScreenOn.Off,
    val orientation: ScreenOrientation = ScreenOrientation.System,
    val corners: CornerStyle = CornerStyle.Normal,
    /** "3 / 12" (the position in the queue) next to the title in Now Playing. */
    val showTrackNumber: Boolean = false,
    /** Immersive: the status bar shows on a swipe. */
    val hideStatusBar: Boolean = false,
)
