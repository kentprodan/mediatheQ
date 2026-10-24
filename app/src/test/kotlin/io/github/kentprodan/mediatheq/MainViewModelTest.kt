package io.github.kentprodan.mediatheq

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val network = FakeNetworkMonitor()
    private val viewModel by lazy { MainViewModel(repository, network) }

    @Test
    fun `the session is unknown until read, then follows the repository`() =
        runTest {
            assertThat(viewModel.session.value).isNull()

            viewModel.session.test {
                assertThat(awaitItem()).isEqualTo(SessionState.LoggedOut())
                repository.session.value = SessionState.LoggedIn("Home", "Ana")
                assertThat(awaitItem()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
            }
        }

    @Test
    fun `logout signs out`() =
        runTest {
            repository.session.value = SessionState.LoggedIn("Home", "Ana")

            viewModel.logout()

            assertThat(repository.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `online follows the network`() =
        runTest {
            viewModel.online.test {
                assertThat(awaitItem()).isTrue()
                network.online.value = false
                assertThat(awaitItem()).isFalse()
            }
        }
}
