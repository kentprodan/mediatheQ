package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParentalControlsTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val controls = ParentalControls(LibraryPreferencesStore(dataStore, activeId = "kid"))

    @Test
    fun `ratings map to ages, unknown ones are not gated`() {
        assertThat(ratingAge("PG-13")).isEqualTo(13)
        assertThat(ratingAge("TV-MA")).isEqualTo(17)
        assertThat(ratingAge("FSK-12")).isEqualTo(12)
        assertThat(ratingAge("16+")).isEqualTo(16)
        assertThat(ratingAge("de/18")).isEqualTo(18)
        assertThat(ratingAge("NR")).isNull()
        assertThat(ratingAge(null)).isNull()

        assertThat(aboveLimit("R", 12)).isTrue()
        assertThat(aboveLimit("PG", 12)).isFalse()
        assertThat(aboveLimit("R", null)).isFalse()
        assertThat(aboveLimit("Unrated", 7)).isFalse()
    }

    @Test
    fun `the PIN is checked against its salted hash and the limit is this profile's`() =
        runTest {
            assertThat(controls.checkPin("1234")).isTrue()
            controls.setPin("2580")

            assertThat(controls.hasPin.first()).isTrue()
            assertThat(controls.checkPin("2580")).isTrue()
            assertThat(controls.checkPin("1234")).isFalse()

            controls.setLimit("kid", 12)
            controls.setLimit("parent", 18)
            assertThat(controls.limit.first()).isEqualTo(12)
            assertThat(controls.limits.first()).containsExactly("kid", 12, "parent", 18)
        }
}
