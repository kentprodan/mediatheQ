package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class LyricsTest {
    private val synced =
        Lyrics(listOf(LyricLine("one", 2.seconds), LyricLine("two", 5.seconds), LyricLine("three", 9.seconds)))

    @Test
    fun `no line is current before the first one starts`() {
        assertThat(synced.currentLineIndex(1.seconds)).isEqualTo(-1)
    }

    @Test
    fun `a line is current from its start`() {
        assertThat(synced.currentLineIndex(5.seconds)).isEqualTo(1)
    }

    @Test
    fun `a line stays current until the next one starts`() {
        assertThat(synced.currentLineIndex(8.seconds)).isEqualTo(1)
    }

    @Test
    fun `the last line stays current to the end`() {
        assertThat(synced.currentLineIndex(300.seconds)).isEqualTo(2)
    }

    @Test
    fun `of lines starting together the last one is current`() {
        val lyrics = Lyrics(listOf(LyricLine("a", 1.seconds), LyricLine("b", 1.seconds)))
        assertThat(lyrics.currentLineIndex(1.seconds)).isEqualTo(1)
    }

    @Test
    fun `plain lyrics have no current line`() {
        val plain = Lyrics(listOf(LyricLine("one"), LyricLine("two")))
        assertThat(plain.isSynced).isFalse()
        assertThat(plain.currentLineIndex(10.seconds)).isEqualTo(-1)
    }

    @Test
    fun `empty lyrics are not synced`() {
        assertThat(Lyrics(emptyList()).isSynced).isFalse()
        assertThat(Lyrics(emptyList()).currentLineIndex(1.seconds)).isEqualTo(-1)
    }
}
