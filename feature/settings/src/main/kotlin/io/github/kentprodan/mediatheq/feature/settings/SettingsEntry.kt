package io.github.kentprodan.mediatheq.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AboutSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.AudioSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.InterfaceSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.LicensesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaybackSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ProvidersSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey
import io.github.kentprodan.mediatheq.core.navigation.StorageSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.ThemeSettingsKey

/**
 * Registers the Settings hub, its pages, the Audio page and the open-source licenses. [versionName] is the app's;
 * [onOpenShowcase] opens the theme showcase, which lives in the app; [licensesReport] and [licenseNotices] read the
 * build's licenses report and the libraries' NOTICE files (ADR 0022).
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    navigator: Navigator,
    versionName: String,
    onOpenShowcase: () -> Unit,
    licensesReport: () -> String,
    licenseNotices: () -> String,
) {
    val back: () -> Unit = { navigator.back() }
    val openAudio: () -> Unit = { navigator.navigateTo(AudioSettingsKey) }
    entry<SettingsKey> { SettingsHubScreen(onOpen = { navigator.navigateTo(it.key) }, onBack = back) }
    entry<InterfaceSettingsKey> {
        SettingsPageRoute(
            SettingsPage.Interface,
            openAudio,
            back,
            onOpenTheme = { navigator.navigateTo(ThemeSettingsKey) },
        )
    }
    entry<ThemeSettingsKey> { SettingsPageRoute(SettingsPage.Theme, openAudio, back) }
    entry<PlaybackSettingsKey> { SettingsPageRoute(SettingsPage.Playback, openAudio, back) }
    entry<StorageSettingsKey> { SettingsPageRoute(SettingsPage.Storage, openAudio, back) }
    entry<ProvidersSettingsKey> { SettingsPageRoute(SettingsPage.Providers, openAudio, back) }
    entry<AboutSettingsKey> {
        AboutSettingsScreen(versionName, onOpenShowcase, onOpenLicenses = { navigator.navigateTo(LicensesKey) }, back)
    }
    entry<AudioSettingsKey> { AudioSettingsRoute(onBack = back) }
    entry<LicensesKey> { LicensesRoute(licensesReport, licenseNotices, onBack = back) }
}

/** The page a hub entry opens. */
internal val SettingsHubEntry.key: NavKey
    get() =
        when (this) {
            SettingsHubEntry.Interface -> InterfaceSettingsKey
            SettingsHubEntry.Playback -> PlaybackSettingsKey
            SettingsHubEntry.Storage -> StorageSettingsKey
            SettingsHubEntry.Providers -> ProvidersSettingsKey
            SettingsHubEntry.About -> AboutSettingsKey
        }
