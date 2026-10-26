package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

private const val MIT_TEXT =
    "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated " +
        "documentation files (the \"Software\"), to deal in the Software without restriction."

private const val NOTICE_TEXT = "# Notices for Media3\n\nThis content is produced and maintained by the project."

private val entries =
    listOf(
        APP_ENTRY,
        LicenseEntry("material-symbols", "Material Symbols (Rounded)", "Icons", "Apache-2.0", "Apache text", null),
        LicenseEntry("dolby-mark", "Dolby double-D mark", "Logo", "Trademark of Dolby Laboratories", null, null),
        LicenseEntry(
            "androidx.media3:media3-exoplayer",
            "Media3 ExoPlayer",
            "1.11.1",
            "The Apache License, Version 2.0",
            "Apache text",
            "https://developer.android.com/media/media3",
        ),
        LicenseEntry(
            "com.squareup.okhttp3:okhttp",
            "okhttp",
            "5.1.0",
            "MIT License",
            MIT_TEXT,
            "https://square.github.io",
        ),
    )

@Composable
private fun Licenses(openId: String? = null) =
    MediatheqTheme { LicensesScreen(LicensesUiState.Content(entries, openId), {}, {}) }

@PreviewTest
@MqPreviews
@Composable
fun LicensesScreenshot() = Licenses()

/** A library open with its NOTICE file under its license (#87). */
@PreviewTest
@MqPreviews
@Composable
fun LicensesOpenNoticeScreenshot() =
    MediatheqTheme {
        LicensesScreen(
            LicensesUiState.Content(
                entries.map {
                    if (it.id == "androidx.media3:media3-exoplayer") it.copy(notice = NOTICE_TEXT) else it
                },
                openId = "androidx.media3:media3-exoplayer",
            ),
            {},
            {},
        )
    }

/** The app's own license open (GPL notice and source link). */
@PreviewTest
@MqPreviews
@Composable
fun LicensesOpenScreenshot() = Licenses(openId = "mediatheq")
