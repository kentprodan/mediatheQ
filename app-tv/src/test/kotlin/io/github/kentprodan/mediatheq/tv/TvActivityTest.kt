package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.testing.TestClock
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
            val viewModel = TvMainViewModel(sessions, FakeAccountRepository(), gate())
            backgroundScope.launch { viewModel.session.collect {} }

            assertThat(viewModel.session.filterNotNull().first()).isInstanceOf(SessionState.LoggedOut::class.java)
            sessions.session.value = SessionState.LoggedIn("Home", "Ana")
            assertThat(viewModel.session.filterNotNull().first()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
        }

    private fun gate() = ProfileGate(ApplicationProvider.getApplicationContext(), TestClock())

    private fun account(id: String) = Account(id, "Home", id, "http://nas", active = id == "a")

    @Test
    fun `Who is watching shows when due with two profiles, not with one, and always when asked for (#433)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts = FakeAccountRepository(listOf(account("a")))
            val gate = gate()
            val viewModel = TvMainViewModel(FakeSessionRepository(), accounts, gate)
            backgroundScope.launch { viewModel.showProfiles.collect {} }

            gate.onForeground()
            runCurrent()
            assertThat(viewModel.showProfiles.value).isFalse()
            accounts.accounts.value = listOf(account("a"), account("b"))
            runCurrent()
            assertThat(viewModel.showProfiles.value).isTrue()

            viewModel.closeProfiles()
            gate.onForeground()
            runCurrent()
            assertThat(viewModel.showProfiles.value).isFalse()
            viewModel.openProfiles()
            runCurrent()
            assertThat(viewModel.showProfiles.value).isTrue()
        }
}
