package io.github.kentprodan.mediatheq.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AudioSettingsKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey

/**
 * Registers Settings and its Audio page. [versionName] is the app's; [onOpenShowcase] opens the theme showcase, which
 * lives in the app.
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    navigator: Navigator,
    versionName: String,
    onOpenShowcase: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsRoute(
            versionName = versionName,
            onOpenAudio = { navigator.navigateTo(AudioSettingsKey) },
            onOpenShowcase = onOpenShowcase,
            onBack = { navigator.back() },
        )
    }
    entry<AudioSettingsKey> { AudioSettingsRoute(onBack = { navigator.back() }) }
}
