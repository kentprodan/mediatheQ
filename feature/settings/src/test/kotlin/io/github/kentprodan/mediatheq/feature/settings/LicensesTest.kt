package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class LicensesTest {
    /** A small report in the shape the AboutLibraries plugin writes. */
    private val report =
        """
        {
          "libraries": [
            {"uniqueId": "com.squareup.okhttp3:okhttp", "name": "okhttp", "artifactVersion": "5.1.0",
             "website": "https://square.github.io/okhttp/", "licenses": ["Apache-2.0"], "funding": []},
            {"uniqueId": "androidx.media3:media3-exoplayer", "name": "Media3 ExoPlayer", "artifactVersion": "1.11.1",
             "licenses": ["Apache-2.0", "MIT"]},
            {"uniqueId": "org.example:nameless", "licenses": ["BSD-3-Clause"]}
          ],
          "licenses": {
            "Apache-2.0": {"name": "The Apache License, Version 2.0", "content": "Apache text", "hash": "Apache-2.0"},
            "MIT": {"name": "MIT License", "content": "MIT text"}
          }
        }
        """.trimIndent()

    private val entries = licenseEntries(report)

    @Test
    fun `the app comes first, then the copied assets, then the libraries by name`() {
        assertThat(entries.map { it.id })
            .containsExactly(
                "mediatheq",
                "material-symbols",
                "dolby-mark",
                "androidx.media3:media3-exoplayer",
                "com.squareup.okhttp3:okhttp",
                "org.example:nameless",
            ).inOrder()
    }

    @Test
    fun `the app shows its license and its source`() {
        assertThat(entries.first().licenseName).isEqualTo("GPL-3.0-or-later")
        assertThat(entries.first().url).isEqualTo("https://github.com/kentprodan/mediatheQ")
    }

    @Test
    fun `copied icons take the Apache text from the report, the Dolby mark has none`() {
        val byId = entries.associateBy { it.id }

        assertThat(byId.getValue("material-symbols").licenseText).isEqualTo("Apache text")
        assertThat(byId.getValue("dolby-mark").licenseText).isNull()
    }

    @Test
    fun `a library shows its version and every license, and its id when it has no name`() {
        val media3 = entries.single { it.id == "androidx.media3:media3-exoplayer" }
        val nameless = entries.single { it.id == "org.example:nameless" }

        assertThat(media3.detail).isEqualTo("1.11.1")
        assertThat(media3.licenseName).isEqualTo("The Apache License, Version 2.0 / MIT License")
        assertThat(media3.licenseText).isEqualTo("Apache text\n\nMIT text")
        assertThat(nameless.name).isEqualTo("org.example:nameless")
        assertThat(nameless.licenseName).isEqualTo("BSD-3-Clause")
    }

    @Test
    fun `the copied assets are the ones THIRD_PARTY md lists`() {
        val rows =
            File(System.getProperty("mediatheq.thirdParty"))
                .readLines()
                .filter { it.startsWith("| ") && !it.startsWith("| What") }
                .map { it.removePrefix("| ").substringBefore(" |") }

        assertThat(rows).hasSize(COPIED_ASSETS.size)
        COPIED_ASSETS.forEach { asset -> assertThat(rows.any { it.startsWith(asset.name) }).isTrue() }
    }
}
