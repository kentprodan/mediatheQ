package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserDataTest {
    @Test
    fun `half stars are the server rating`() {
        assertThat(UserData(rating = 8.0).halfStars).isEqualTo(8)
        assertThat(UserData(rating = 7.0).halfStars).isEqualTo(7)
        assertThat(UserData(rating = 10.0).halfStars).isEqualTo(10)
        assertThat(UserData(rating = null).halfStars).isNull()
    }

    @Test
    fun `ratings set elsewhere round to half stars, at least half a star`() {
        assertThat(UserData(rating = 6.6).halfStars).isEqualTo(7)
        assertThat(UserData(rating = 0.4).halfStars).isEqualTo(1)
        assertThat(UserData(rating = 12.0).halfStars).isEqualTo(10)
    }
}
