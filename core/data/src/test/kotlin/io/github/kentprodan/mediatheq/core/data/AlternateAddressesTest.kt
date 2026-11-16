package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlternateAddressesTest {
    @Test
    fun `one address per line, blanks and trailing slashes dropped, duplicates once`() {
        assertThat(alternateAddresses(" http://100.64.0.2:8096/ \n\nhttps://jf.example.org\nhttps://jf.example.org/"))
            .containsExactly("http://100.64.0.2:8096", "https://jf.example.org")
            .inOrder()
        assertThat(alternateAddresses("")).isEmpty()
    }

    @Test
    fun `anything that is not an http(s) address refuses the whole list`() {
        assertThat(alternateAddresses("http://ok:8096\nftp://no")).isNull()
        assertThat(alternateAddresses("100.64.0.2:8096")).isNull()
    }
}
