package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ProfilesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the signed-in accounts are the profiles and picking one makes it active (#443)`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts =
                FakeAccountRepository(
                    listOf(
                        Account("a", "Home", "Cristian", "http://nas", active = true),
                        Account("b", "Home", "Ana", "http://nas", active = false),
                        Account("c", "Home", "Old", "http://nas", active = false, signedIn = false),
                    ),
                )
            val viewModel = ProfilesViewModel(accounts)
            backgroundScope.launch { viewModel.profiles.collect() }
            runCurrent()

            assertThat(viewModel.profiles.value.map { it.userName }).containsExactly("Cristian", "Ana").inOrder()
            viewModel.switchTo("b")
            runCurrent()
            assertThat(accounts.activated).containsExactly("b")
        }
}
