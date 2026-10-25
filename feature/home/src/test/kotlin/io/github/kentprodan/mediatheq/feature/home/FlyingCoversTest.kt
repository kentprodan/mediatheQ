package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

class FlyingCoversTest {
    private fun album(name: String) = Album(MediaId(ServerId("s"), name), name, emptyList())

    private val a = album("a")
    private val b = album("b")
    private val c = album("c")

    @Test
    fun `an album in several rows flies only from its first row`() {
        val flying = flyingCovers(listOf(listOf(a, b), listOf(b, c), listOf(a, c)))

        assertThat(flying).containsExactly(setOf(a.id, b.id), setOf(c.id), emptySet<MediaId>()).inOrder()
    }

    @Test
    fun `an empty row is skipped without hiding later covers`() {
        val flying = flyingCovers(listOf(emptyList(), listOf(a), listOf(b)))

        assertThat(flying).containsExactly(emptySet<MediaId>(), setOf(a.id), setOf(b.id)).inOrder()
    }
}
