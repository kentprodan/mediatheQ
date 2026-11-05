package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The Settings hub, opened from Home: categories that open their own pages (#120). */
@Serializable
data object SettingsKey : NavKey

/** Settings -> Interface: colors, album grid, ratings. */
@Serializable
data object InterfaceSettingsKey : NavKey

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

/** Settings -> Playback: streaming quality, normalization, Audio. */
@Serializable
data object PlaybackSettingsKey : NavKey

/** Settings -> Offline, cache, and download. */
@Serializable
data object StorageSettingsKey : NavKey

/** Settings -> Manage media providers: the server account, Log out. */
@Serializable
data object ProvidersSettingsKey : NavKey

/** Settings -> About: version, licenses. */
@Serializable
data object AboutSettingsKey : NavKey

/** Settings -> Playback -> Audio: bit-perfect USB and the equalizer. */
@Serializable
data object AudioSettingsKey : NavKey

/** Settings -> About -> Open-source licenses (#70). */
@Serializable
data object LicensesKey : NavKey
