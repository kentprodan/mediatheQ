package io.github.kentprodan.mediatheq.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AudioSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.LicensesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey

/**
 * Registers Settings, its Audio page and the open-source licenses. [versionName] is the app's; [onOpenShowcase] opens
 * the theme showcase, which lives in the app; [licensesReport] and [licenseNotices] read the build's licenses report and
 * the libraries' NOTICE files (ADR 0022).
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    navigator: Navigator,
    versionName: String,
    onOpenShowcase: () -> Unit,
    licensesReport: () -> String,
    licenseNotices: () -> String,
) {
    entry<SettingsKey> {
        SettingsRoute(
            versionName = versionName,
            onOpenAudio = { navigator.navigateTo(AudioSettingsKey) },
            onOpenShowcase = onOpenShowcase,
            onOpenLicenses = { navigator.navigateTo(LicensesKey) },
            onBack = { navigator.back() },
        )
    }
    entry<AudioSettingsKey> { AudioSettingsRoute(onBack = { navigator.back() }) }
    entry<LicensesKey> { LicensesRoute(licensesReport, licenseNotices, onBack = { navigator.back() }) }
}
