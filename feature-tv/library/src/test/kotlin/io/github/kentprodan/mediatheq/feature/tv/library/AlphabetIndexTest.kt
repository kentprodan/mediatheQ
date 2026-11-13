package io.github.kentprodan.mediatheq.feature.tv.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlphabetIndexTest {
    @Test
    fun `each letter points at its first name, accents and case ignored, the rest under #`() {
        val names = listOf("2 Unlimited", "!!!", "abba", "Air", "Élan", "eels", "Zebra", "Ålesund")

        assertThat(alphabetIndex(names))
            .containsExactly(Letter('#', 0), Letter('A', 2), Letter('E', 4), Letter('Z', 6))
            .inOrder()
    }

    @Test
    fun `an empty list has no letters`() {
        assertThat(alphabetIndex(emptyList())).isEmpty()
    }
}
