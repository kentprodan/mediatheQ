package io.github.kentprodan.mediatheq.feature.tv.settings

import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle

/** The HDMI output whose Dolby Atmos passthrough switch Settings shows (ADR 0039). */
data class TvHdmiOutput(
    val id: OutputId,
    val name: String,
    val atmosPassthrough: Boolean,
)

/** The dialog open over the settings list. */
enum class TvSettingsDialog {
    Quality,
    SignOut,
    AppMode,
    SubtitleSize,
    SubtitleBackground,
    SubtitleColor,
    DisplayMatch,
    CarouselSource,
}

/** Settings on TV (#243). */
sealed interface TvSettingsUiState {
    data object Loading : TvSettingsUiState

    data class Content(
        /**
         * The HDMI output the switch edits: the current one, else the last one remembered (the player names it on
         * its first play over HDMI); `null` before that.
         */
        val hdmi: TvHdmiOutput?,
        /** The streaming quality on unmetered networks, where the TV plays. */
        val quality: StreamingQuality,
        val sync: SyncStatus,
        val serverName: String?,
        val userName: String?,
        val dialog: TvSettingsDialog? = null,
        /** What the TV shows (#426, ADR 0051); `null` when the server has no video, so the row is hidden. */
        val appMode: AppMode? = null,
        /** How subtitles look in the video player (#429). */
        val subtitleStyle: SubtitleStyle = SubtitleStyle(),
        /** How the display follows a video (#384). */
        val displayMatch: io.github.kentprodan.mediatheq.core.data.DisplayMatch =
            io.github.kentprodan.mediatheq.core.data.DisplayMatch.Rate,
        /** Transitions between pages (#444): Simple when on. */
        val simpleTransitions: Boolean = false,
        /** Home's featured carousel (#521): its style, its source (`null` = automatic) and whether it skips watched. */
        val carousel: io.github.kentprodan.mediatheq.core.data.TvCarousel =
            io.github.kentprodan.mediatheq.core.data.TvCarousel.Card,
        val carouselSource: io.github.kentprodan.mediatheq.core.model.HomeSection? = null,
        val carouselSkipWatched: Boolean = true,
        /** The sections the carousel can show in the app mode (#521). */
        val carouselSources: List<io.github.kentprodan.mediatheq.core.model.HomeSection> = emptyList(),
    ) : TvSettingsUiState
}

sealed interface TvSettingsAction {
    /** Turns the HDMI output's Dolby Atmos passthrough on or off; the next track follows it (#241). */
    data object TogglePassthrough : TvSettingsAction

    data object ChooseQuality : TvSettingsAction

    data class SetQuality(
        val quality: StreamingQuality,
    ) : TvSettingsAction

    data object SyncNow : TvSettingsAction

    data object ChooseAppMode : TvSettingsAction

    data object ToggleSimpleTransitions : TvSettingsAction

    /** Settings -> Display -> Home carousel (#521): Off, Card, Immersive in turn. */
    data object NextCarousel : TvSettingsAction

    data object ChooseCarouselSource : TvSettingsAction

    /** `null` = automatic. */
    data class SetCarouselSource(
        val source: io.github.kentprodan.mediatheq.core.model.HomeSection?,
    ) : TvSettingsAction

    data object ToggleCarouselSkipWatched : TvSettingsAction

    /** Opens the dialog of one part of the subtitle style. */
    data class ChooseSubtitle(
        val dialog: TvSettingsDialog,
    ) : TvSettingsAction

    data class SetDisplayMatch(
        val match: io.github.kentprodan.mediatheq.core.data.DisplayMatch,
    ) : TvSettingsAction

    data class SetSubtitleStyle(
        val style: SubtitleStyle,
    ) : TvSettingsAction

    data class SetAppMode(
        val mode: AppMode,
    ) : TvSettingsAction

    /** Asks before signing out. */
    data object AskSignOut : TvSettingsAction

    data object SignOut : TvSettingsAction

    /** Closes the open dialog. */
    data object Dismiss : TvSettingsAction
}
