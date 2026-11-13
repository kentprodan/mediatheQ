package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Plain Application: the screens get fixed state, no Hilt graph. A 1080p TV.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the placeholder Home opens with focus on its first card, and the D-pad moves it`() {
        composeRule.setContent { MediatheqTvTheme { PlaceholderScreen() } }

        composeRule.onNodeWithText("mediatheQ").assertExists()
        composeRule.onNodeWithText("Home").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Library").assertIsFocused()
    }

    @Test
    fun `the session decides between onboarding and Home`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val sessions = FakeSessionRepository()
            val viewModel = TvMainViewModel(sessions)
            backgroundScope.launch { viewModel.session.collect {} }

            assertThat(viewModel.session.filterNotNull().first()).isInstanceOf(SessionState.LoggedOut::class.java)
            sessions.session.value = SessionState.LoggedIn("Home", "Ana")
            assertThat(viewModel.session.filterNotNull().first()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
        }
}
