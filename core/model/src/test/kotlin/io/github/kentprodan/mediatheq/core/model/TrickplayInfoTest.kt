package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Tile index and cell math (#277): 10 x 10 thumbnails a tile, one every 10 s, 250 in all. */
class TrickplayInfoTest {
    private val tiles = TrickplayInfo("s", 320, 180, 10, 10, 250, 10.seconds)

    @Test
    fun `a position finds its tile, column and row`() {
        assertThat(tiles.cellAt(0.seconds)).isEqualTo(TrickplayCell(0, 0, 0))
        assertThat(tiles.cellAt(125.seconds)).isEqualTo(TrickplayCell(0, 2, 1))
        assertThat(tiles.cellAt(1005.seconds)).isEqualTo(TrickplayCell(1, 0, 0))
    }

    @Test
    fun `past the last thumbnail it stays on the last, and the tile count rounds up`() {
        assertThat(tiles.cellAt(99_999.seconds)).isEqualTo(TrickplayCell(2, 9, 4))
        assertThat(tiles.tileCount).isEqualTo(3)
    }
}
