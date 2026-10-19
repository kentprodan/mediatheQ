package io.github.kentprodan.mediatheq.feature.onboarding.server

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingServerKey

/** Registers the server address screen. */
fun EntryProviderScope<NavKey>.serverEntry(navigator: Navigator) {
    entry<OnboardingServerKey> { ServerRoute(onNavigate = navigator::navigateTo) }
}
