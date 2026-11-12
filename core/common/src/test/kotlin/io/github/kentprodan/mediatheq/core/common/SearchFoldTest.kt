package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchFoldTest {
    @Test
    fun `case and diacritics are dropped`() {
        assertThat(foldForSearch("Moșoi ȘTEFAN")).isEqualTo("mosoi stefan")
    }
}
