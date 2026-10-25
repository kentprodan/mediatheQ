package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.LyricDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.LyricLineDto
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LyricsMappersTest {
    @Test
    fun `starts are ticks of 100 ns`() {
        val lyrics = LyricDto(listOf(LyricLineDto("a", start = 12_345_670_000))).toDomain()

        assertThat(lyrics.lines.single().start).isEqualTo(1_234_567.milliseconds)
    }

    @Test
    fun `R4 synced lines are dropped without a start and sorted by start`() {
        val lyrics =
            LyricDto(
                listOf(
                    LyricLineDto("late", start = 50_000_000),
                    LyricLineDto("untimed"),
                    LyricLineDto("early", start = 10_000_000),
                ),
            ).toDomain()

        assertThat(lyrics.isSynced).isTrue()
        assertThat(lyrics.lines).containsExactly(LyricLine("early", 1.seconds), LyricLine("late", 5.seconds)).inOrder()
    }

    @Test
    fun `plain lines keep the server's order`() {
        val lyrics = LyricDto(listOf(LyricLineDto("b"), LyricLineDto("a"))).toDomain()

        assertThat(lyrics.isSynced).isFalse()
        assertThat(lyrics.lines.map { it.text }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `no lines map to empty lyrics`() {
        assertThat(LyricDto().toDomain().lines).isEmpty()
    }
}
