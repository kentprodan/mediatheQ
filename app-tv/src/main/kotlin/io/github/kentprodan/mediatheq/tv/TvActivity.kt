package io.github.kentprodan.mediatheq.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.OnboardingServerKey
import io.github.kentprodan.mediatheq.feature.tv.onboarding.tvOnboardingEntries

/**
 * The TV app's one activity (ADR 0038): onboarding while signed out (ADR 0040), the placeholder Home once signed in
 * (#235 brings the shell). A 401 signs out in the repository (ADR 0036), and the onboarding starts again at the server.
 * shortcut: no Android 17 local network permission gate (the Streamer runs Android 14); port the phone's
 * LocalNetworkGate when a TV runs Android 17.
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediatheqTvTheme { TvApp() } }
    }
}

@Composable
private fun TvApp(viewModel: TvMainViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (session) {
        null -> Unit
        is SessionState.LoggedOut -> TvOnboardingHost()
        is SessionState.LoggedIn -> PlaceholderScreen()
    }
}

/** Signed-out flow with its own back stack: server -> Quick Connect -> password. Back goes one screen back. */
@Composable
internal fun TvOnboardingHost() {
    val backStack = rememberNavBackStack(OnboardingServerKey)
    val navigator = remember(backStack) { StackNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.back() },
        entryDecorators =
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider { tvOnboardingEntries(navigator) },
    )
}

/** [Navigator] over a single back stack; Back on the first screen leaves the app. */
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
