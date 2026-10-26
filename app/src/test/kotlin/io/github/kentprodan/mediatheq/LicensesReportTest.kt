package io.github.kentprodan.mediatheq

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/** The licenses the release build ships, as the AboutLibraries plugin generates them (#70, ADR 0022). */
class LicensesReportTest {
    private val report: JsonObject =
        Json.parseToJsonElement(File(System.getProperty("mediatheq.licensesReport")).readText()).jsonObject

    private val libraries = report.getValue("libraries").jsonArray.map { it.jsonObject }
    private val ids = libraries.map { it.getValue("uniqueId").jsonPrimitive.content }

    @Test
    fun `the libraries the app ships are listed`() {
        assertThat(ids).containsAtLeast(
            "androidx.media3:media3-exoplayer",
            "com.squareup.okhttp3:okhttp",
            "com.google.dagger:hilt-android",
            "io.coil-kt.coil3:coil-compose",
        )
    }

    @Test
    fun `the app's own license ships with it, as in LICENSE`() {
        val gpl =
            report
                .getValue("licenses")
                .jsonObject
                .getValue("GPL-3.0-or-later")
                .jsonObject
        val license = File(System.getProperty("mediatheq.license")).readText().replace("\r\n", "\n").trim()

        assertThat(
            gpl
                .getValue("content")
                .jsonPrimitive.content
                .trim(),
        ).isEqualTo(license)
    }

    @Test
    fun `the NOTICE files of shipped libraries are collected (#87)`() {
        val notices =
            Json.parseToJsonElement(File(System.getProperty("mediatheq.licenseNotices")).readText()).jsonObject

        val jakarta = notices.getValue("jakarta.inject:jakarta.inject-api").jsonPrimitive.content
        assertThat(jakarta).startsWith("# Notices for Eclipse Jakarta Dependency Injection")
        // Every notice belongs to a library the app ships.
        assertThat(ids).containsAtLeastElementsIn(notices.keys)
    }

    @Test
    fun `debug-only tools are not listed`() {
        assertThat(ids).containsNoneOf("androidx.compose.ui:ui-tooling", "androidx.compose.ui:ui-test-manifest")
    }

    @Test
    fun `every library has a license with its full text`() {
        val licenses = report.getValue("licenses").jsonObject
        libraries.forEach { library ->
            val ids = library.getValue("licenses").jsonArray.map { it.jsonPrimitive.content }
            assertThat(ids).isNotEmpty()
            ids.forEach { id ->
                val text =
                    licenses[id]
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.content
                assertThat(text.orEmpty().length).isGreaterThan(500)
            }
        }
    }
}
