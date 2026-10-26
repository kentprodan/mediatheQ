package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLibraryPreferencesRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultLibraryPreferencesRepository(LibraryPreferencesStore(dataStore))

    @Test
    fun `recent searches come newest first, without repeats, at most ten`() =
        runTest {
            (1..12).forEach { repository.addRecentSearch("q$it") }
            repository.addRecentSearch("Q5")

            assertThat(repository.recentSearches.first())
                .containsExactly("Q5", "q12", "q11", "q10", "q9", "q8", "q7", "q6", "q4", "q3")
                .inOrder()
        }

    @Test
    fun `a recent search is one line and blank ones are ignored`() =
        runTest {
            repository.addRecentSearch("  pasted\nwith   lines  ")
            repository.addRecentSearch(" \n ")

            assertThat(repository.recentSearches.first()).containsExactly("pasted with lines")
        }

    @Test
    fun `recent searches can be cleared`() =
        runTest {
            repository.addRecentSearch("mosoi")

            repository.clearRecentSearches()

            assertThat(repository.recentSearches.first()).isEmpty()
        }

    @Test
    fun `defaults are chips, two columns and each tab's first sort`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences).isEqualTo(LibraryPreferences())
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Name)
        }

    @Test
    fun `choices are kept`() =
        runTest {
            repository.setFilterStyle(FilterControlStyle.ConnectedButtons)
            repository.setAlbumColumns(4)
            repository.setSort(LibraryFilter.Albums, LibrarySort.Year)
            repository.setSort(LibraryFilter.Songs, LibrarySort.Album)

            val preferences = repository.preferences.first()
            assertThat(preferences.filterStyle).isEqualTo(FilterControlStyle.ConnectedButtons)
            assertThat(preferences.albumColumns).isEqualTo(4)
            assertThat(preferences.sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.sortFor(LibraryFilter.Songs)).isEqualTo(LibrarySort.Album)
        }

    @Test
    fun `values a tab does not offer are refused`() =
        runTest {
            repository.setAlbumColumns(7)
            repository.setSort(LibraryFilter.Artists, LibrarySort.Year)

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `old or broken stored values fall back to defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("filter_style")] = "Carousel"
                it[intPreferencesKey("album_columns")] = 9
                it[stringPreferencesKey("sort_Songs")] = "Year"
                it[stringPreferencesKey("sort_Gone")] = "Name"
            }

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `an unreadable file gives the defaults`() =
        runTest {
            dataStore.failing = true

            assertThat(repository.preferences.first()).isEqualTo(LibraryPreferences())
        }

    @Test
    fun `display defaults are Iris colors and whole stars`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences.dynamicColor).isFalse()
            assertThat(preferences.offlineMode).isFalse()
            assertThat(preferences.ratingMode).isEqualTo(RatingMode.WholeStars)
        }

    @Test
    fun `display choices are kept, and an unknown rating mode means whole stars`() =
        runTest {
            repository.setDynamicColor(true)
            repository.setRatingMode(RatingMode.HalfStars)
            repository.setOfflineMode(true)
            assertThat(repository.preferences.first().dynamicColor).isTrue()
            assertThat(repository.preferences.first().offlineMode).isTrue()
            assertThat(repository.preferences.first().ratingMode).isEqualTo(RatingMode.HalfStars)

            dataStore.edit { it[stringPreferencesKey("rating_mode")] = "Hearts" }
            assertThat(repository.preferences.first().ratingMode).isEqualTo(RatingMode.WholeStars)
        }
}
