package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class InputGuardTest {
    @Test
    fun `a whole file read once passes, reading it again past twice its size stops the decode`() {
        val guard = InputGuard(fileBytes = 1_000)

        repeat(20) { guard.read(100) }
        assertThat(guard.bytesRead).isEqualTo(2_000)

        assertThrows(EndlessInputException::class.java) { guard.read(1) }
    }
}
