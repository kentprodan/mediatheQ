package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.LicenseTextKey
import io.github.kentprodan.mediatheq.core.navigation.LicensesKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey

/**
 * Registers Settings on TV (#243), a drawer destination, with its licenses list and license pages. [versionName],
 * [report] and [notices] come from the TV app (ADR 0022), like on the phone.
 */
fun EntryProviderScope<NavKey>.tvSettingsEntries(
    navigator: Navigator,
    versionName: String,
    report: () -> String,
    notices: () -> String,
) {
    entry<SettingsKey> { TvSettingsRoute(versionName, onOpenLicenses = { navigator.navigateTo(LicensesKey) }) }
    entry<LicensesKey> { TvLicensesRoute(report, notices, onOpen = { navigator.navigateTo(LicenseTextKey(it)) }) }
    entry<LicenseTextKey> { TvLicenseRoute(it.id, report, notices) }
}
