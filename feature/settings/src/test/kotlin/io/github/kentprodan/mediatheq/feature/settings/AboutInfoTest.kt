package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URLDecoder

class AboutInfoTest {
    private val info = AboutInfo("0.1.0", 1, "17", "Google Pixel 9")

    private fun query(url: String) =
        URI(url).rawQuery.split("&").associate { part ->
            part.substringBefore("=") to URLDecoder.decode(part.substringAfter("="), Charsets.UTF_8)
        }

    @Test
    fun `the summary names the app version, Android and the device`() {
        assertThat(info.summary()).isEqualTo("mediatheQ 0.1.0 (1), Android 17, Google Pixel 9")
    }

    @Test
    fun `each kind opens its template with the summary as the device, and nothing else`() {
        val bug = supportIssueUrl(SupportKind.Issue, info)
        val feature = supportIssueUrl(SupportKind.FeatureRequest, info)

        assertThat(bug).startsWith("https://github.com/kentprodan/mediatheQ/issues/new?")
        assertThat(query(bug)).containsExactly("template", "bug.yml", "device", info.summary())
        assertThat(query(feature)).containsExactly("template", "feature_request.yml", "device", info.summary())
    }

    @Test
    fun `a device name with URL characters stays inside the device field`() {
        val odd = info.copy(device = "Maker & Co #1 ?x=y")

        val url = supportIssueUrl(SupportKind.Issue, odd)

        assertThat(URI(url).rawFragment).isNull()
        assertThat(query(url)).containsExactly("template", "bug.yml", "device", odd.summary())
    }

    @Test
    fun `both templates exist and have the device field the URL fills`() {
        SupportKind.entries.forEach { kind ->
            // The tests run in the module's folder.
            val lines = File("../../.github/ISSUE_TEMPLATE/${kind.template}").readLines().map { it.trim() }

            assertThat(lines).containsAtLeast("- type: input", "id: device").inOrder()
            assertThat(lines[lines.indexOf("id: device") - 1]).isEqualTo("- type: input")
        }
    }
}
