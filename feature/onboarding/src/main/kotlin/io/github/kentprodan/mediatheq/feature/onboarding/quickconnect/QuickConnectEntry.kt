package io.github.kentprodan.mediatheq.feature.onboarding.quickconnect

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey

/** Registers the Quick Connect screen; Cancel goes back to the login form. */
fun EntryProviderScope<NavKey>.quickConnectEntry(navigator: Navigator) {
    entry<OnboardingQuickConnectKey> { key -> QuickConnectRoute(key, onCancel = { navigator.back() }) }
}
