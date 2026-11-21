package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButtonStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvIconButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvImageButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvTextField
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvWideButton

/**
 * The `MqTv*` components in their states (enabled, disabled, selected). Layoutlib renders no focus (tv-material draws it from the real focus
 * state), so the focused look is proven on Robolectric by the app's focus test and on the Streamer.
 */
@Composable
private fun States(content: @Composable (Boolean) -> Unit) =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                content(true)
                content(false)
            }
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvButtonScreenshot() = States { enabled -> MqTvButton("Play", {}, enabled = enabled) }

/** The guideline's six button types (#519): filled, outlined, icon, outline icon, wide, image. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvButtonTypesScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MqTvButton("Play", {}, icon = MqTvIcons.Play)
                    MqTvButton("From the start", {}, style = MqTvButtonStyle.Outlined)
                    MqTvIconButton(MqTvIcons.FavoriteFilled, "Favorite", {}, outlined = false)
                    MqTvIconButton(MqTvIcons.Favorite, "Favorite", {})
                    MqTvIconButton(MqTvIcons.More, "More", {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MqTvWideButton("Audio and subtitles", {}, subtitle = "English 5.1 · Off", icon = MqTvIcons.Settings)
                    MqTvImageButton(
                        PreviewFixtures.ARTIST,
                        {},
                        Modifier.width(140.dp).height(210.dp),
                        supporting = "Self",
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                    }
                }
            }
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvCardScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            // tv-material cards have no disabled state: with and without a subtitle.
            Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                listOf(PreviewFixtures.ARTIST, null).forEach { subtitle ->
                    MqTvCard(PreviewFixtures.ALBUM, {}, subtitle = subtitle) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                    }
                }
            }
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvListItemScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp).width(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST)
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST, selected = true)
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST, enabled = false)
            }
        }
    }

/** A row with a switch (#243): on, off, and disabled. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvListItemSwitchScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp).width(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to true, false to true, true to false).forEach { (on, enabled) ->
                    MqTvListItem(
                        "Dolby Atmos passthrough",
                        {},
                        supporting = "Sends Dolby Atmos unchanged to the soundbar",
                        enabled = enabled,
                        trailing = { Switch(checked = on, onCheckedChange = null, enabled = enabled) },
                    )
                }
            }
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvTextFieldScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp).width(480.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                MqTvTextField("192.168.1.10:8096", {}, "Server address")
                MqTvTextField("secret", {}, "Password", password = true)
                MqTvTextField("", {}, "User name")
            }
        }
    }

/** The guideline's five card variants (#518), unfocused; MqTvCardFocusTest proves the focused look. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvCardVariantsScreenshot() =
    MediatheqTvTheme {
        androidx.tv.material3.Surface {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier
                    .padding(24.dp),
                horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(20.dp),
            ) {
                io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCardStyle.entries.forEach { style ->
                    io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard(
                        style.name,
                        {},
                        subtitle = "2024",
                        imageAspectRatio = if (style.wide) 16f / 9f else 2f / 3f,
                        style = style,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics
                                        .Color(0xFF3A4A5A),
                                ),
                        )
                    }
                }
            }
        }
    }
