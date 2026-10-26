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
