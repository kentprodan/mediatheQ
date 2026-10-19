package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingServerKey
import io.github.kentprodan.mediatheq.feature.onboarding.login.loginEntry
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.quickConnectEntry
import io.github.kentprodan.mediatheq.feature.onboarding.server.serverEntry

/** Signed-out flow with its own back stack, separate from the tabs: server -> login -> Quick Connect. */
@Composable
fun OnboardingHost() {
    val backStack = rememberNavBackStack(OnboardingServerKey)
    val navigator = remember(backStack) { StackNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.back() },
        entryDecorators =
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider =
            entryProvider {
                serverEntry(navigator)
                loginEntry(navigator)
                quickConnectEntry(navigator)
            },
    )
}

/** [Navigator] over a single back stack. */
private class StackNavigator(
    private val backStack: NavBackStack<NavKey>,
) : Navigator {
    override fun navigateTo(key: NavKey) {
        backStack.add(key)
    }

    override fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
