package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadKeyTest {
    private val id = MediaId(ServerId("s1"), "cc4b4fdb31a8af38cfcb812d79ad565c")

    @Test
    fun `a track or video key is its id, as music keys always were`() {
        assertThat(DownloadKey.Item(id).toString()).isEqualTo(id.toString())
        assertThat(DownloadKey.parse(id.toString())).isEqualTo(DownloadKey.Item(id))
    }

    @Test
    fun `a subtitle key names its video and stream index, and parses back`() {
        val key = DownloadKey.Subtitle(id, 3)

        assertThat(key.toString()).isEqualTo("$id:sub:3")
        assertThat(DownloadKey.parse(key.toString())).isEqualTo(key)
    }

    @Test
    fun `anything else is no key`() {
        assertThat(DownloadKey.parse("$id:sub:x")).isNull()
        assertThat(DownloadKey.parse("nothing")).isNull()
    }
}
