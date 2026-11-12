package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

private val info = AboutInfo("0.1.0", 1, "17", "Google Pixel 9")

/** About in a release build: mark, name, Version, Changelog, licenses, Source code, Privacy. */
@PreviewTest
@MqPreviews
@Composable
fun AboutScreenshot() =
    MediatheqTheme {
        AboutScreen(info, {}, {}, {}, {}, {}, onOpenShowcase = null, onExportLogs = {}) {}
    }

/** About in a debug build: the theme showcase too. */
@PreviewTest
@MqPreviews
@Composable
fun AboutDebugScreenshot() =
    MediatheqTheme {
        AboutScreen(info, {}, {}, {}, {}, {}, onOpenShowcase = {}, onExportLogs = {}) {}
    }

/** Privacy: what stays on the phone, what is sent where. */
@PreviewTest
@MqPreviews
@Composable
fun PrivacyScreenshot() = MediatheqTheme { PrivacyScreen {} }

/** The changelog, on the sheet's color (popups render blank in screenshot tests): the newest version open. */
@PreviewTest
@MqPreviews
@Composable
fun ChangelogScreenshot() =
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ChangelogContent(
                ChangelogUiState.Content(
                    listOf(
                        ChangelogVersion(
                            "Unreleased",
                            null,
                            listOf(
                                ChangelogSection("Added", listOf("Contact support through GitHub.", "A privacy page.")),
                                ChangelogSection("Fixed", listOf("The cursor no longer jumps back while typing.")),
                            ),
                        ),
                        ChangelogVersion(
                            "0.1.0",
                            "2026-10-01",
                            listOf(ChangelogSection("Added", listOf("First build."))),
                        ),
                    ),
                    open = setOf("Unreleased"),
                ),
            ) {}
        }
    }

/** Contact support, in place (dialogs render blank in screenshot tests). */
@PreviewTest
@MqPreviews
@Composable
fun ContactSupportScreenshot() =
    MediatheqTheme { MqDialogContent("Contact support", null, {}, "Cancel", {}) { ContactSupportContent {} } }
