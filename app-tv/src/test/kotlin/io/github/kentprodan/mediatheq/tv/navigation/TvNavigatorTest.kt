package io.github.kentprodan.mediatheq.tv.navigation

import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import org.junit.Test

class TvNavigatorTest {
    private val album = AlbumKey(MediaId(ServerId("s"), "a"))
    private val artist = ArtistKey(MediaId(ServerId("s"), "r"))

    private fun stack(vararg keys: NavKey) = mutableListOf(*keys)

    @Test
    fun `a drawer entry replaces the top level, with Home kept below it`() {
        val stack = stack(HomeKey, LibraryKey, album, artist)

        TvNavigator.navigate(stack, SearchKey)
        assertThat(stack).containsExactly(HomeKey, SearchKey).inOrder()

        TvNavigator.navigate(stack, HomeKey)
        assertThat(stack).containsExactly(HomeKey)
    }

    @Test
    fun `a detail page pushes, and the same page twice is one entry`() {
        val stack = stack(HomeKey, LibraryKey)

        TvNavigator.navigate(stack, album)
        TvNavigator.navigate(stack, album)
        TvNavigator.navigate(stack, NowPlayingKey)

        assertThat(stack).containsExactly(HomeKey, NowPlayingKey).inOrder()
        TvNavigator.navigate(stack, artist)
        assertThat(stack).containsExactly(HomeKey, NowPlayingKey, artist).inOrder()
    }

    @Test
    fun `back pops a detail, lands on Home from another entry, and leaves on Home`() {
        val stack = stack(HomeKey, LibraryKey, album)

        assertThat(TvNavigator.back(stack)).isTrue()
        assertThat(stack).containsExactly(HomeKey, LibraryKey).inOrder()
        assertThat(TvNavigator.back(stack)).isTrue()
        assertThat(stack).containsExactly(HomeKey)
        assertThat(TvNavigator.back(stack)).isFalse()
        assertThat(stack).containsExactly(HomeKey)
    }
}
