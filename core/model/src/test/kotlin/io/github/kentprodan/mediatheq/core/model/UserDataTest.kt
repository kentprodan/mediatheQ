package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserDataTest {
    @Test
    fun `stars are the server rating halved`() {
        assertThat(UserData(rating = 8.0).stars).isEqualTo(4)
        assertThat(UserData(rating = 10.0).stars).isEqualTo(5)
        assertThat(UserData(rating = null).stars).isNull()
    }

    @Test
    fun `ratings set elsewhere round to 1-5 stars`() {
        assertThat(UserData(rating = 7.0).stars).isEqualTo(4)
        assertThat(UserData(rating = 0.5).stars).isEqualTo(1)
        assertThat(UserData(rating = 12.0).stars).isEqualTo(5)
    }

    @Test
    fun `ratingOf maps stars back to the server scale`() {
        assertThat((1..5).map { UserData.ratingOf(it) }).containsExactly(2.0, 4.0, 6.0, 8.0, 10.0).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ratingOf rejects 0 stars`() {
        UserData.ratingOf(0)
    }
}
