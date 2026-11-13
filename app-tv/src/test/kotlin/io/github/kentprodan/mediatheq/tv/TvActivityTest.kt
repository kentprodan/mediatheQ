package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.SessionState
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

// Plain Application: no Hilt graph. A 1080p TV.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvActivityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
