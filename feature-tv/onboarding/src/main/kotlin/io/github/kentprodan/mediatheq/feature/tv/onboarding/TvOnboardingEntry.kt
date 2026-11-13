package io.github.kentprodan.mediatheq.feature.tv.onboarding

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingServerKey
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginRoute
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectRoute
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerRoute

/**
 * The TV's sign-in steps (ADR 0040): server -> Quick Connect -> (password). Signing in changes the session and the app
 * leaves onboarding by itself.
 */
fun EntryProviderScope<NavKey>.tvOnboardingEntries(navigator: Navigator) {
    entry<OnboardingServerKey> { TvServerRoute(onNavigate = navigator::navigateTo) }
    entry<OnboardingQuickConnectKey> { key ->
        TvQuickConnectRoute(
            key,
            onUsePassword = { navigator.navigateTo(OnboardingLoginKey(key.baseUrl, key.serverName)) },
        )
    }
    entry<OnboardingLoginKey> { TvLoginRoute(it) }
}
