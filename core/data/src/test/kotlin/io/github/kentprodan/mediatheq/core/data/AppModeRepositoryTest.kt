package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppModeRepositoryTest {
    private val store = LibraryPreferencesStore(InMemoryPreferencesDataStore())

    private fun repository(video: Boolean = true) = DefaultAppModeRepository(store, ServerCapabilities(video))

    @Test
    fun `a missing mode reads as Music and not chosen`() =
        runTest {
            val repository = repository()

            assertThat(repository.mode.first()).isEqualTo(AppMode.Music)
            assertThat(repository.chosen.first()).isFalse()
        }

    @Test
    fun `a stored mode reads back and counts as chosen`() =
        runTest {
            val repository = repository()

            repository.setMode(AppMode.Video)

            assertThat(repository.mode.first()).isEqualTo(AppMode.Video)
            assertThat(repository.chosen.first()).isTrue()
        }

    @Test
    fun `without video capability the mode is Music whatever is stored`() =
        runTest {
            repository().setMode(AppMode.Both)

            assertThat(repository(video = false).mode.first()).isEqualTo(AppMode.Music)
            assertThat(store.values.first().appMode).isEqualTo("Both")
        }
}
