package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RedactTest {
    @Test
    fun `a URL keeps its host and path, never its query`() {
        assertThat(redact("GET http://100.64.0.2:8096/Audio/abc/stream?ApiKey=s3cret&static=true failed"))
            .isEqualTo("GET http://100.64.0.2:8096/Audio/abc/stream?<redacted> failed")
        assertThat(redact("ws://nas/socket?api_key=s3cret&deviceId=x")).isEqualTo("ws://nas/socket?<redacted>")
    }

    @Test
    fun `keys, passwords and header values go in any case`() {
        val text =
            "apikey=s3cret API_KEY: s3cret password=hunter2 Authorization: MediaBrowser Token=\"s3cret\" " +
                "x-emby-token: s3cret"

        assertThat(redact(text)).doesNotContain("s3cret")
        assertThat(redact(text)).doesNotContain("hunter2")
        assertThat(
            redact("MediaBrowser Client=\"mediatheQ\", Token=\"s3cret\""),
        ).isEqualTo("MediaBrowser Client=\"mediatheQ\", Token=\"<redacted>\"")
    }

    @Test
    fun `a token in JSON goes, as in a parse error's snippet`() {
        val snippet = """Unexpected JSON token at offset 12: {"AccessToken":"s3cret","ServerId":"abc"}"""

        assertThat(
            redact(snippet),
        ).isEqualTo("""Unexpected JSON token at offset 12: {"AccessToken":"<redacted>","ServerId":"abc"}""")
    }

    @Test
    fun `text without secrets stays as it is`() {
        val text = "Failed to connect to /100.64.0.2:8096 · Sync done: 3120 tracks in 24s"

        assertThat(redact(text)).isEqualTo(text)
    }
}
