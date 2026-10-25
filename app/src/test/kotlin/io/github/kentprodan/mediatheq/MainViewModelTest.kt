package io.github.kentprodan.mediatheq

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val userData = FakeUserDataRepository()
    private val network = FakeNetworkMonitor()
    private val sync = FakeLibrarySyncRepository()
    private val libraryPreferences = FakeLibraryPreferencesRepository()
    private val viewModel by lazy { MainViewModel(repository, userData, network, sync, libraryPreferences) }

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
    fun `a refused favorite or rating is offered once, then dismissed`() =
        runTest {
            userData.error.value = MqError.Network
            assertThat(viewModel.userDataError.value).isEqualTo(MqError.Network)

            viewModel.dismissUserDataError()

            assertThat(viewModel.userDataError.value).isNull()
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

    @Test
    fun `local network access granted now syncs the library at once`() =
        runTest {
            viewModel.onLocalNetworkGranted()

            assertThat(sync.syncCalls).isEqualTo(1)
        }

    @Test
    fun `Material You is unknown until read, then follows the setting`() =
        runTest {
            assertThat(viewModel.dynamicColor.value).isNull()

            viewModel.dynamicColor.test {
                assertThat(awaitItem()).isFalse()
                libraryPreferences.setDynamicColor(true)
                assertThat(awaitItem()).isTrue()
            }
        }
}
