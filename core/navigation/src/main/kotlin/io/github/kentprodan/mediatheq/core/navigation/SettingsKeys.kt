package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.OutputId
import kotlinx.serialization.Serializable

/** The Settings hub, opened from Home: categories that open their own pages (#120). */
@Serializable
data object SettingsKey : NavKey

/** Settings -> Interface: colors, album grid, ratings. */
@Serializable
data object InterfaceSettingsKey : NavKey

/** Settings -> Interface -> App mode: Music, Video or Both (ADR 0051). */
@Serializable
data object AppModeSettingsKey : NavKey

/** Settings -> Interface -> Theme, font and colors (#121). */
@Serializable
data object ThemeSettingsKey : NavKey

/** Settings -> Interface -> Navigation and media start (#122). */
@Serializable
data object NavigationSettingsKey : NavKey

/** Settings -> Interface -> Media lists (#123). */
@Serializable
data object MediaListsSettingsKey : NavKey

/** Settings -> Interface -> Now playing screens (#126). */
@Serializable
data object NowPlayingSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Home page (#127). */
@Serializable
data object HomePageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Library page (#129). */
@Serializable
data object LibraryPageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Album page (#132). */
@Serializable
data object AlbumPageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Artist page (#133). */
@Serializable
data object ArtistPageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Genre page (#134). */
@Serializable
data object GenrePageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Playlist page (#135). */
@Serializable
data object PlaylistPageSettingsKey : NavKey

/** Settings -> Interface -> Page settings -> Search page (#136). */
@Serializable
data object SearchPageSettingsKey : NavKey

/** Settings -> Advanced settings, from the hub and from Interface (#137). */
@Serializable
data object AdvancedSettingsKey : NavKey

/** Settings -> Playback -> Playback: queue and transport behavior (#138). */
@Serializable
data object PlaybackBehaviorSettingsKey : NavKey

/** Settings -> Playback -> Decoding and transcoding (#138). */
@Serializable
data object DecodingSettingsKey : NavKey

/** Settings -> Playback -> Audio focus (#139). */
@Serializable
data object AudioFocusSettingsKey : NavKey

/** Settings -> Playback -> Headset buttons (#139). */
@Serializable
data object HeadsetSettingsKey : NavKey

/** Settings -> Playback -> Notification buttons (#139). */
@Serializable
data object NotificationButtonsSettingsKey : NavKey

/** Settings -> Offline, cache, and download -> General (#144). */
@Serializable
data object StorageGeneralSettingsKey : NavKey

/** Settings -> Offline, cache, and download -> Media (#144). */
@Serializable
data object StorageMediaSettingsKey : NavKey

/** Settings -> Offline, cache, and download -> Playback (#144). */
@Serializable
data object StoragePlaybackSettingsKey : NavKey

/** Settings -> Offline, cache, and download -> Images (#144). */
@Serializable
data object StorageImagesSettingsKey : NavKey

/** Settings -> Playback -> Transitions (#142). */
@Serializable
data object TransitionsSettingsKey : NavKey

/** Settings -> Playback -> Automatic actions (#141). */
@Serializable
data object AutoActionsSettingsKey : NavKey

/** Settings -> Playback: streaming quality, normalization, Audio. */
@Serializable
data object PlaybackSettingsKey : NavKey

/** Settings -> Offline, cache, and download. */
@Serializable
data object StorageSettingsKey : NavKey

/** Settings -> Manage media providers: the server account, Log out. */
@Serializable
data object ProvidersSettingsKey : NavKey

/** Settings -> Backup & restore (#150). */
@Serializable
data object BackupSettingsKey : NavKey

/** Settings -> About (#151): version, changelog, licenses, source code, privacy. */
@Serializable
data object AboutSettingsKey : NavKey

/** Settings -> About -> Privacy (#151): what the app keeps, and what it sends where. */
@Serializable
data object PrivacyKey : NavKey

/** Settings -> Playback -> Output settings (#140): every remembered output, the current one marked. */
@Serializable
data object OutputSettingsKey : NavKey

/** One output's settings and equalizer (#140); `null` = the current output (Now Playing's Equalizer button). */
@Serializable
data class OutputKey(
    @Serializable(with = OutputIdSerializer::class) val id: OutputId? = null,
) : NavKey

/** Settings -> About -> Open-source licenses (#70). */
@Serializable
data object LicensesKey : NavKey

/** One entry's license text, on TV (#243), where a long text gets its own page; [id] is the entry's id. */
@Serializable
data class LicenseTextKey(
    val id: String,
) : NavKey

/** One Home section's own settings (#182), from its gear in Settings -> Home page; the page lives in :feature:home. */
@Serializable
data class HomeSectionSettingsKey(
    val section: HomeSection,
) : NavKey
