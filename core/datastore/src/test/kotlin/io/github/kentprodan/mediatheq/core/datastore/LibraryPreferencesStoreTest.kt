package io.github.kentprodan.mediatheq.core.datastore

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibraryPreferencesStoreTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val store = LibraryPreferencesStore(dataStore)

    @Test
    fun `recent searches read back in stored order`() =
        runTest {
            store.updateRecentSearches { listOf("ada", "mosoi") }

            assertThat(store.recentSearches.first()).containsExactly("ada", "mosoi").inOrder()
        }

    @Test
    fun `genre rows are kept per account, names with commas intact (#431)`() =
        runTest {
            val other = LibraryPreferencesStore(dataStore, activeId = "account-2")

            store.setHomeGenres(listOf("Action, Adventure", "Drama"))

            assertThat(store.homeGenres.first()).containsExactly("Action, Adventure", "Drama").inOrder()
            assertThat(other.homeGenres.first()).isEmpty()
        }

    @Test
    fun `a failed recent search write is dropped instead of crashing`() =
        runTest {
            dataStore.failing = true

            store.updateRecentSearches { listOf("mosoi") }

            dataStore.failing = false
            assertThat(store.recentSearches.first()).isEmpty()
        }
}
