package io.github.kentprodan.mediatheq.feature.tv.onboarding

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingLoginKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingQuickConnectKey
import io.github.kentprodan.mediatheq.core.navigation.OnboardingServerKey
import io.github.kentprodan.mediatheq.core.navigation.ProfilesKey
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginRoute
import io.github.kentprodan.mediatheq.feature.tv.onboarding.profiles.TvProfilesRoute
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
            onUsePassword = {
                val steps = if (key.returnSteps > 0) key.returnSteps + 1 else 0
                navigator.navigateTo(OnboardingLoginKey(key.baseUrl, key.serverName, steps))
            },
            onDone = { repeat(key.returnSteps) { navigator.back() } },
        )
    }
    entry<OnboardingLoginKey> { key -> TvLoginRoute(key, onDone = { repeat(key.returnSteps) { navigator.back() } }) }
}

/**
 * "Who's watching?" (#433) with the sign-in steps to add a profile on the same server; a sign-in returns to the
 * profiles. [onUse] records the choice before a switch restarts the app, [onClose] leaves the picker.
 */
fun EntryProviderScope<NavKey>.tvProfilesEntries(
    navigator: Navigator,
    onUse: () -> Unit,
    onClose: () -> Unit,
) {
    entry<ProfilesKey> {
        TvProfilesRoute(
            onUse = onUse,
            onClose = onClose,
            onAdd = { baseUrl, serverName ->
                navigator.navigateTo(OnboardingQuickConnectKey(baseUrl, serverName, returnSteps = 1))
            },
        )
    }
    tvOnboardingEntries(navigator)
}
