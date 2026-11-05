package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

class YearAlbumsTest {
    private fun album(
        name: String,
        year: Int?,
    ) = Album(MediaId(ServerId("s"), name), name, emptyList(), year = year)

    @Test
    fun `years come newest first with their albums in order, unknown year last`() {
        val albums = listOf(album("a", 1994), album("b", null), album("c", 2001), album("d", 1994))

        val years = albumYears(albums)

        assertThat(years.map { it.year }).containsExactly(2001, 1994, null).inOrder()
        assertThat(years[1].albums.map { it.title }).containsExactly("a", "d").inOrder()
        assertThat(years[2].albums.map { it.title }).containsExactly("b")
    }

    @Test
    fun `no albums, no years`() {
        assertThat(albumYears(emptyList())).isEmpty()
    }
}
