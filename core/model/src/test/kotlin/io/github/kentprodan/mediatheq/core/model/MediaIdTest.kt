package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaIdTest {
    private val server = ServerId("srv1")

    @Test
    fun `ids with same server and item are equal`() {
        assertThat(MediaId(server, "abc")).isEqualTo(MediaId(ServerId("srv1"), "abc"))
    }

    @Test
    fun `ids on different servers are not equal`() {
        assertThat(MediaId(server, "abc")).isNotEqualTo(MediaId(ServerId("srv2"), "abc"))
    }

    @Test
    fun `string form round-trips through parse`() {
        val id = MediaId(server, "f00d:with:colons")
        assertThat(id.toString()).isEqualTo("srv1:f00d:with:colons")
        assertThat(MediaId.parse(id.toString())).isEqualTo(id)
    }

    @Test
    fun `parse rejects a value without server`() {
        assertThrows(IllegalArgumentException::class.java) { MediaId.parse(":abc") }
        assertThrows(IllegalArgumentException::class.java) { MediaId.parse("abc") }
    }

    @Test
    fun `parse rejects an empty item id`() {
        assertThrows(IllegalArgumentException::class.java) { MediaId.parse("srv1:") }
    }

    @Test
    fun `server id cannot contain a colon`() {
        assertThrows(IllegalArgumentException::class.java) { ServerId("a:b") }
    }
}
