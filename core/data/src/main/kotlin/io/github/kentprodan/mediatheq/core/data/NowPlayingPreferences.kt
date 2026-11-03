package io.github.kentprodan.mediatheq.core.data

/** How the mini player shows the position (#126). */
enum class MiniProgress { Hidden, Line, Background }

/** What is behind Now Playing: the cover's color plain or as a gradient, or the app theme's background. */
enum class PlayerBackground { Solid, Gradient, Theme }

/** Now Playing's progress bar: a waveform style, or a plain bar that never uses the waveform. */
enum class PlayerProgress {
    WaveformSharp,
    WaveformSmooth,
    WaveformBars,
    WaveformBarsReflected,
    Basic,
    Wavy,
    ;

    /** A waveform style; Basic and Wavy are plain bars that never use one. */
    val usesWaveform: Boolean get() = this != Basic && this != Wavy
}

/** Now Playing's bar while a track has no waveform yet. */
enum class ProgressFallback { Basic, Wavy }

/** What a tap on Now Playing's cover does. */
enum class CoverTap { PlayPause, ShowLyrics, None }

/** Settings -> Interface -> Now playing screens (#126). */
data class NowPlayingPreferences(
    /** A floating pill; off = docked full width on the bottom bar (only with the bottom bar style). */
    val floatingMiniPlayer: Boolean = true,
    val miniSwipeToSkip: Boolean = true,
    val miniProgress: MiniProgress = MiniProgress.Background,
    val background: PlayerBackground = PlayerBackground.Gradient,
    val progress: PlayerProgress = PlayerProgress.WaveformSharp,
    val fallback: ProgressFallback = ProgressFallback.Basic,
    /** The stars show when this is on and ratings are not off. */
    val showRating: Boolean = true,
    val coverTap: CoverTap = CoverTap.PlayPause,
    val coverSwipe: Boolean = true,
    val playButtonWhenPaused: Boolean = false,
)
