package io.github.kentprodan.mediatheq.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import org.junit.Test

class FlyingCoversTest {
    private fun album(name: String) = Album(MediaId(ServerId("s"), name), name, emptyList())

    private val a = album("a")
    private val b = album("b")
    private val c = album("c")

    @Test
    fun `an album in several rows flies from every row, each card with its own key`() {
        // #418: Back flew the cover to the album's first card on Home, not to the card it was opened from.
        val flying = flyingCovers(listOf(listOf(a, b), listOf(b, c), listOf(a, c)))

        assertThat(flying).containsExactly(setOf(a.id, b.id), setOf(b.id, c.id), setOf(a.id, c.id)).inOrder()
        assertThat(sharedCoverKey(b.id, "RecentlyAdded")).isNotEqualTo(sharedCoverKey(b.id, "RecentlyPlayed"))
        assertThat(sharedCoverKey(b.id)).isEqualTo("cover-${b.id}")
    }

    @Test
    fun `the album page gets the card it was opened from`() {
        val navigation = mutableListOf<AlbumKey>()
        val home =
            HomeNavigation(onOpenAlbumFrom = { id, origin -> navigation += AlbumKey(id, origin) })

        home.onOpenAlbumFrom(b.id, "RecentlyPlayed")

        assertThat(navigation.single().coverOrigin).isEqualTo("RecentlyPlayed")
    }
}
