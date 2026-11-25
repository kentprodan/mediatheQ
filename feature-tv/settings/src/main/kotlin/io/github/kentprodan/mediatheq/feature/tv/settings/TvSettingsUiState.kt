package io.github.kentprodan.mediatheq.feature.tv.settings

import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle

/** The HDMI output whose Dolby Atmos passthrough switch Settings shows (ADR 0039). */
data class TvHdmiOutput(
    val id: OutputId,
    val name: String,
    val atmosPassthrough: Boolean,
)

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
        /** What the TV shows (#426, ADR 0051); `null` when the server has no video, so the row is hidden. */
        val appMode: AppMode? = null,
        /** How subtitles look in the video player (#429). */
        val subtitleStyle: SubtitleStyle = SubtitleStyle(),
        /** How the display follows a video (#384). */
        val displayMatch: io.github.kentprodan.mediatheq.core.data.DisplayMatch =
            io.github.kentprodan.mediatheq.core.data.DisplayMatch.System,
        /** Video -> Playback details (#670): what the engine decided, over the picture. */
        val playbackDetails: Boolean = false,
        /** Transitions between pages (#444): Simple when on. */
        val simpleTransitions: Boolean = false,
        /**
         * Home's featured carousel (#521): its style, the sections it mixes (#575: the chosen ones, else the default)
         * and whether it skips watched.
         */
        val carousel: TvCarousel =
            TvCarousel.Card,
        val carouselPicked: List<HomeSection> = emptyList(),
        val carouselSkipWatched: Boolean = true,
        /** The sections the carousel can show in the app mode (#521). */
        val carouselSources: List<HomeSection> = emptyList(),
        /** The side menu's style (#520). */
        val menuStyle: io.github.kentprodan.mediatheq.core.data.TvMenuStyle =
            io.github.kentprodan.mediatheq.core.data.TvMenuStyle.Modal,
        /** Each detail page's title (#523, #619). */
        val titleStyles: Map<TvTitlePage, TvTitleStyle> = emptyMap(),
        /** Each detail page's logo size (#578, #619). */
        val logoSizes: Map<TvTitlePage, TvLogoSize> = emptyMap(),
        /** Display -> Accent from the poster (#517). */
        val posterAccent: Boolean = false,
        /** Screens -> Home -> Home shows (#621): music too, or movies and series only. */
        val homeMusic: Boolean = true,
    ) : TvSettingsUiState
}

sealed interface TvSettingsAction {
    /** Turns the HDMI output's Dolby Atmos passthrough on or off; the next track follows it (#241). */
    data object TogglePassthrough : TvSettingsAction

    data class SetQuality(
        val quality: StreamingQuality,
    ) : TvSettingsAction

    data object SyncNow : TvSettingsAction

    data object ToggleSimpleTransitions : TvSettingsAction

    data object TogglePlaybackDetails : TvSettingsAction

    /** Settings -> Home -> Carousel (#521): Off, Card or Immersive. */
    data class SetCarousel(
        val carousel: TvCarousel,
    ) : TvSettingsAction

    /** Turns a carousel section on or off (#575); the last one stays on. */
    data class ToggleCarouselSource(
        val source: HomeSection,
    ) : TvSettingsAction

    data object ToggleCarouselSkipWatched : TvSettingsAction

    /** Settings -> Display -> Menu style (#520). */
    data class SetMenuStyle(
        val style: io.github.kentprodan.mediatheq.core.data.TvMenuStyle,
    ) : TvSettingsAction

    /** Screens -> a page -> Title on pages (#523, #619): Logo, Logo and name or Name only. */
    data class SetTitleStyle(
        val page: TvTitlePage,
        val style: TvTitleStyle,
    ) : TvSettingsAction

    /** Screens -> a page -> Logo size (#578, #619). */
    data class SetLogoSize(
        val page: TvTitlePage,
        val size: TvLogoSize,
    ) : TvSettingsAction

    /** Display -> Accent from the poster (#517). */
    data object TogglePosterAccent : TvSettingsAction

    data class SetDisplayMatch(
        val match: io.github.kentprodan.mediatheq.core.data.DisplayMatch,
    ) : TvSettingsAction

    data class SetSubtitleStyle(
        val style: SubtitleStyle,
    ) : TvSettingsAction

    data class SetAppMode(
        val mode: AppMode,
    ) : TvSettingsAction

    /** Screens -> Home -> Home shows (#621). */
    data class SetHomeMusic(
        val music: Boolean,
    ) : TvSettingsAction

    data object SignOut : TvSettingsAction
}
