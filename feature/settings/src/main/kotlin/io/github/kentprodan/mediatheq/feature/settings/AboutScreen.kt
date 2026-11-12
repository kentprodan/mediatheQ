package io.github.kentprodan.mediatheq.feature.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.kentprodan.mediatheq.core.designsystem.component.MqAppMark
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import kotlinx.coroutines.launch

/** Settings -> About (#151); the Changelog opens as a sheet here. */
@Composable
internal fun AboutRoute(
    info: AboutInfo,
    changelog: () -> String,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenShowcase: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var changelogOpen by rememberSaveable { mutableStateOf(false) }
    AboutScreen(
        info,
        // Android 13+ confirms the copy itself.
        onCopyVersion = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("mediatheQ", info.summary()))) }
        },
        onOpenChangelog = { changelogOpen = true },
        onOpenLicenses = onOpenLicenses,
        // No app may open links (no browser): then the row does nothing rather than crash.
        onOpenSource = { runCatching { uriHandler.openUri(SOURCE_URL) } },
        onOpenPrivacy = onOpenPrivacy,
        onOpenShowcase = onOpenShowcase.takeIf { info.debug },
        onBack = onBack,
    )
    if (changelogOpen) ChangelogSheet(changelog, onDismiss = { changelogOpen = false })
}

/**
 * Settings -> About: the app's mark and name, then Version (a tap or a long press copies it with Android and the
 * device), Changelog, Open-source licenses, Source code, Privacy and, in debug builds, the theme showcase. Stateless.
 */
@Composable
fun AboutScreen(
    info: AboutInfo,
    onCopyVersion: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenShowcase: (() -> Unit)?,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_about), onBack) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = MediatheqSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MqAppMark()
                Text(
                    "mediatheQ",
                    Modifier.padding(top = MediatheqSpacing.s).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.settings_about_tagline),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "rows") {
            Group(R.string.settings_about) {
                item { shapes ->
                    SegmentedListItem(
                        onClick = onCopyVersion,
                        shapes = shapes,
                        colors = settingsColors(),
                        onLongClick = onCopyVersion,
                        onLongClickLabel = stringResource(R.string.settings_version_copy),
                        leadingContent = { Icon(MqIcons.Info, contentDescription = null) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_version_summary, info.versionName, info.versionCode))
                        },
                    ) { Text(stringResource(R.string.settings_version)) }
                }
                item { shapes ->
                    AboutRow(
                        shapes,
                        MqIcons.History,
                        R.string.settings_changelog,
                        R.string.settings_changelog_summary,
                        onOpenChangelog,
                    )
                }
                item { shapes ->
                    AboutRow(
                        shapes,
                        MqIcons.Checklist,
                        R.string.settings_license,
                        R.string.settings_license_summary,
                        onOpenLicenses,
                    )
                }
                item { shapes ->
                    AboutRow(
                        shapes,
                        MqIcons.Code,
                        R.string.settings_source,
                        R.string.settings_source_summary,
                        onOpenSource,
                    )
                }
                item { shapes ->
                    AboutRow(
                        shapes,
                        MqIcons.PrivacyTip,
                        R.string.settings_privacy,
                        R.string.settings_privacy_summary,
                        onOpenPrivacy,
                    )
                }
                onOpenShowcase?.let { open ->
                    item { shapes ->
                        AboutRow(
                            shapes,
                            MqIcons.Palette,
                            R.string.settings_showcase,
                            R.string.settings_showcase_summary,
                            open,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    shapes: androidx.compose.material3.ListItemShapes,
    icon: ImageVector,
    title: Int,
    summary: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = settingsColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}
