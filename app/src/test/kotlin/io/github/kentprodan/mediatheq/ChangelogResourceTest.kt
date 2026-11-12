package io.github.kentprodan.mediatheq

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.feature.settings.parseChangelog
import org.junit.Test
import java.io.File

/** The changelog the release build bundles (#151): the repository's CHANGELOG.md, as the app reads it. */
class ChangelogResourceTest {
    private val bundled = File(System.getProperty("mediatheq.changelog")!!).readText()
    private val source = File(System.getProperty("mediatheq.changelogSource")!!).readText()

    @Test
    fun `the build bundles CHANGELOG md as it is`() {
        assertThat(bundled).isEqualTo(source)
    }

    @Test
    fun `every release has sections with items, the newest first`() {
        val versions = parseChangelog(bundled)

        assertThat(versions).isNotEmpty()
        versions.forEach { version ->
            // Right after a release, Unreleased is still empty.
            if (!version.unreleased) assertThat(version.sections).isNotEmpty()
            version.sections.forEach { assertThat(it.items).isNotEmpty() }
        }
        // The sheet keys its rows by name: a version listed twice would crash it.
        assertThat(versions.map { it.name }).containsNoDuplicates()
        val released = versions.mapNotNull { it.date }
        assertThat(released).isInStrictOrder(reverseOrder<String>())
    }
}
