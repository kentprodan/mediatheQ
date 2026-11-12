package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChangelogTest {
    @Test
    fun `versions keep the file's order with their dates, sections and items`() {
        val text =
            """
            # Changelog

            Some prose about the format.

            ## [Unreleased]

            ### Added
            - Contact support
              through GitHub.

            ## [0.2.0] - 2026-11-12
            ### Fixed
            * The cursor no longer jumps.
            ### Changed
            - Faster sync.

            ## 0.1.0 - 2026-10-01

            [0.2.0]: https://github.com/kentprodan/mediatheQ/releases/tag/v0.2.0
            """.trimIndent()

        val versions = parseChangelog(text)

        assertThat(versions)
            .containsExactly(
                ChangelogVersion(
                    "Unreleased",
                    null,
                    listOf(ChangelogSection("Added", listOf("Contact support through GitHub."))),
                ),
                ChangelogVersion(
                    "0.2.0",
                    "2026-11-12",
                    listOf(
                        ChangelogSection("Fixed", listOf("The cursor no longer jumps.")),
                        ChangelogSection("Changed", listOf("Faster sync.")),
                    ),
                ),
                ChangelogVersion("0.1.0", "2026-10-01", emptyList()),
            ).inOrder()
        assertThat(versions.map { it.unreleased }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `lines outside a version or a section are skipped`() {
        val text =
            """
            ### Added
            - before any version
            ## [1.0.0] - 2026-01-01
            - before any section
            ###
            ### Fixed
            -
            - real
            not an item
            """.trimIndent()

        assertThat(parseChangelog(text))
            .containsExactly(
                ChangelogVersion("1.0.0", "2026-01-01", listOf(ChangelogSection("Fixed", listOf("real")))),
            )
    }

    @Test
    fun `every second-level heading starts a version, also a yanked or linked one`() {
        val text =
            """
            ## [1.0.0](https://example.org) - 2026-02-01
            ### Added
            - a
            ## [0.0.5] - 2014-02-25 [YANKED]
            ### Fixed
            - b
            ## Something odd
            ### Fixed
            - c
            """.trimIndent()

        assertThat(parseChangelog(text).map { Triple(it.name, it.date, it.sections.single().items) })
            .containsExactly(
                Triple("1.0.0", "2026-02-01", listOf("a")),
                Triple("0.0.5", "2014-02-25", listOf("b")),
                Triple("Something odd", null, listOf("c")),
            ).inOrder()
    }

    @Test
    fun `an empty file has no versions`() {
        assertThat(parseChangelog("")).isEmpty()
        assertThat(parseChangelog("# Changelog\n\nNothing yet.")).isEmpty()
    }
}
