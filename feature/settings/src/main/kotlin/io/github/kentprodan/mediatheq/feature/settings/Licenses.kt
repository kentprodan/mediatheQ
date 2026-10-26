package io.github.kentprodan.mediatheq.feature.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The app's license id in the build's report, which carries its full text (from LICENSE). */
private const val APP_LICENSE = "GPL-3.0-or-later"

/** The app itself, first in the list (ADR 0001); the full GPL text follows its notice in [licenseEntries]. */
internal val APP_ENTRY =
    LicenseEntry(
        id = "mediatheq",
        name = "mediatheQ",
        detail = null,
        licenseName = "GPL-3.0-or-later",
        licenseText =
            "mediatheQ is free software: you can redistribute it and/or modify it under the terms of the GNU General " +
                "Public License as published by the Free Software Foundation, either version 3 of the License, or " +
                "(at your option) any later version.\n\n" +
                "mediatheQ is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even " +
                "the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General " +
                "Public License for more details: https://www.gnu.org/licenses/gpl-3.0.html",
        url = "https://github.com/kentprodan/mediatheQ",
    )

/** Code and assets copied into the repository; keep in step with `THIRD_PARTY.md` (a test checks it). */
internal data class CopiedAsset(
    val id: String,
    val name: String,
    val detail: String,
    /** A license id of the build's report (its text comes from there), or `null` for a trademark. */
    val licenseId: String?,
    val licenseName: String,
    val url: String,
)

internal val COPIED_ASSETS =
    listOf(
        CopiedAsset(
            "material-symbols",
            "Material Symbols (Rounded)",
            "Icons",
            "Apache-2.0",
            "Apache-2.0",
            "https://github.com/google/material-design-icons",
        ),
        CopiedAsset(
            "dolby-mark",
            "Dolby double-D mark",
            "Logo",
            null,
            "Trademark of Dolby Laboratories",
            "https://www.dolby.com",
        ),
    )

/**
 * The licenses list: the app, then the copied assets, then every library of the build's report (AboutLibraries JSON,
 * ADR 0022) by name, each with its NOTICE from [noticesJson] (`{"group:name": text}`, #87) when it has one.
 */
internal fun licenseEntries(
    reportJson: String,
    noticesJson: String,
): List<LicenseEntry> {
    val report = json.decodeFromString<Report>(reportJson)
    val notices = json.decodeFromString<Map<String, String>>(noticesJson)
    val assets =
        COPIED_ASSETS.map { asset ->
            LicenseEntry(
                asset.id,
                asset.name,
                asset.detail,
                asset.licenseName,
                asset.licenseId?.let { report.licenses[it]?.content },
                asset.url,
            )
        }
    val libraries =
        report.libraries
            .map { library ->
                val licenses = library.licenses.mapNotNull { report.licenses[it] }
                LicenseEntry(
                    id = library.uniqueId,
                    name = library.name ?: library.uniqueId,
                    detail = library.artifactVersion,
                    licenseName = licenses.map { it.name }.ifEmpty { library.licenses }.joinToString(" / "),
                    licenseText = licenses.mapNotNull { it.content }.joinToString("\n\n").ifEmpty { null },
                    url = library.website,
                    notice = notices[library.uniqueId],
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val appText = listOfNotNull(APP_ENTRY.licenseText, report.licenses[APP_LICENSE]?.content).joinToString("\n\n")
    val app = APP_ENTRY.copy(licenseText = appText)
    return listOf(app) + assets + libraries
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class Report(
    val libraries: List<Library> = emptyList(),
    val licenses: Map<String, License> = emptyMap(),
)

@Serializable
private class Library(
    val uniqueId: String,
    val name: String? = null,
    val artifactVersion: String? = null,
    val website: String? = null,
    val licenses: List<String> = emptyList(),
)

@Serializable
private class License(
    val name: String,
    val content: String? = null,
)
