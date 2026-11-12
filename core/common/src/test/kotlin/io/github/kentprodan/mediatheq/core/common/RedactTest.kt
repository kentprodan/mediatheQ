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
        ).isEqualTo("MediaBrowser Client=\"mediatheQ\", Token=<redacted>")
    }

    @Test
    fun `a token in JSON goes, as in a parse error's snippet`() {
        val snippet = """Unexpected JSON token at offset 12: {"AccessToken":"s3cret","ServerId":"abc"}"""

        assertThat(
            redact(snippet),
        ).isEqualTo("""Unexpected JSON token at offset 12: {"AccessToken":"<redacted>","ServerId":"abc"}""")
    }

    @Test
    fun `quoted, spaced and escaped values go whole`() {
        val texts =
            listOf(
                "password=\"hunter 2\" next",
                "ApiKey: \"s3cret\"",
                "Login failed: password=correct horse s3cret",
                """{"Pw":"a\"s3cret"}""",
                "StoredAccount(url=http://nas, accessToken=s3cret)",
                "Token=s3cret",
            )

        texts.forEach { assertThat(redact(it)).doesNotContain("s3cret") }
        texts.forEach { assertThat(redact(it)).doesNotContain("hunter") }
        assertThat(redact("Login failed: password=correct horse s3cret")).doesNotContain("horse")
    }

    @Test
    fun `the Quick Connect secret goes, from JSON and from text`() {
        assertThat(
            redact("""bad JSON {"Secret":"s3cret","Code":"123456"}"""),
        ).isEqualTo("""bad JSON {"Secret":"<redacted>","Code":"123456"}""")
        assertThat(redact("secret=s3cret")).doesNotContain("s3cret")
    }

    @Test
    fun `text without secrets stays as it is`() {
        val text = "Failed to connect to /100.64.0.2:8096 · Sync done: 3120 tracks in 24s"

        assertThat(redact(text)).isEqualTo(text)
    }
}
