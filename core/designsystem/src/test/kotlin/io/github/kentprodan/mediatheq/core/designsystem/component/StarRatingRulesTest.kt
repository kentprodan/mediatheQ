package io.github.kentprodan.mediatheq.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StarRatingRulesTest {
    @Test
    fun `whole stars rate with the star, and the current star clears`() {
        assertThat(starTap(null, 3, allowHalf = false)).isEqualTo(6)
        assertThat(starTap(6, 3, allowHalf = false)).isEqualTo(null)
        assertThat(starTap(6, 5, allowHalf = false)).isEqualTo(10)
    }

    @Test
    fun `whole stars treat a half star rating as rounded`() {
        // 3.5 shows as 4 stars: the 4th star is the current one.
        assertThat(starTap(7, 4, allowHalf = false)).isNull()
        assertThat(starTap(7, 3, allowHalf = false)).isEqualTo(6)
    }

    @Test
    fun `half stars cycle whole, half, cleared on the same star`() {
        assertThat(starTap(null, 4, allowHalf = true)).isEqualTo(8)
        assertThat(starTap(8, 4, allowHalf = true)).isEqualTo(7)
        assertThat(starTap(7, 4, allowHalf = true)).isNull()
    }

    @Test
    fun `half stars on another star rate it whole`() {
        assertThat(starTap(7, 2, allowHalf = true)).isEqualTo(4)
        assertThat(starTap(1, 5, allowHalf = true)).isEqualTo(10)
    }

    @Test
    fun `each star is empty, half or full`() {
        assertThat((1..5).map { starFill(7, it) })
            .containsExactly(StarFill.Full, StarFill.Full, StarFill.Full, StarFill.Half, StarFill.Empty)
            .inOrder()
        assertThat((1..5).map { starFill(null, it) }.toSet()).containsExactly(StarFill.Empty)
        assertThat(starFill(1, 1)).isEqualTo(StarFill.Half)
    }
}
