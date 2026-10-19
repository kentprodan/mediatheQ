package io.github.kentprodan.mediatheq.feature.onboarding.login

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey

/** Registers the username + password screen. */
fun EntryProviderScope<NavKey>.loginEntry(navigator: Navigator) {
    entry<OnboardingLoginKey> { key -> LoginRoute(key, onNavigate = navigator::navigateTo) }
}
