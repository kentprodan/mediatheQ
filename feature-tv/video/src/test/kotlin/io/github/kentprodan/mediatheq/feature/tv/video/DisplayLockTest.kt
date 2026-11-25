package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

class DisplayLockTest {
    private val film = MediaId(ServerId("s"), "f")
    private val other = MediaId(ServerId("s"), "o")

    @Test
    fun `a reload or a second measurement of the same video never switches again (#678)`() {
        val start = nextDisplayLock(null, film, 2160, measured = null, library = 23.976f, stream = null)
        val measured = nextDisplayLock(start, film, 2160, measured = 23.98f, library = 23.976f, stream = null)
        // A reload: the measurement starts over and its first guess is wrong.
        val reloaded = nextDisplayLock(measured, film, 2160, measured = 29.97f, library = 23.976f, stream = 30f)

        assertThat(start?.format).isEqualTo(23.976f to 2160)
        assertThat(measured?.format).isEqualTo(23.976f to 2160)
        assertThat(reloaded?.format).isEqualTo(23.976f to 2160)
    }

    @Test
    fun `the first measurement may still turn a pulldown film to 59_94, and a new video starts over`() {
        val start = nextDisplayLock(null, film, 480, measured = null, library = 23.976f, stream = null)
        val pulldown = nextDisplayLock(start, film, 480, measured = 59.94f, library = 23.976f, stream = null)
        val next = nextDisplayLock(pulldown, other, 2160, measured = null, library = 25f, stream = null)

        assertThat(pulldown?.format).isEqualTo(59.94f to 480)
        assertThat(next?.format).isEqualTo(25f to 2160)
    }
}
