package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCornerScale
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions

/** Covers (medium and extraLarge), a card, a dialog and a button at one corner size (#137). */
@Composable
private fun Corners(scale: MqCornerScale) =
    MediatheqTheme(MqThemeOptions(cornerScale = scale)) {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m)) {
                    MqCover(
                        rememberPreviewCover(),
                        null,
                        Modifier.width(CoverWidth),
                        shape = MaterialTheme.shapes.medium,
                    )
                    MqCover(
                        rememberPreviewCover(),
                        null,
                        Modifier.width(CoverWidth),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
                Card { Text("Card", Modifier.padding(MediatheqSpacing.l)) }
                OutlinedButton(onClick = {}) { Text("Pill button stays round") }
                MqDialogContent("Rename", "Save", {}, "Cancel", {}) { Text("Dialog content") }
            }
        }
    }

@PreviewTest
@MqPreviews
@Composable
fun CornersSmallScreenshot() = Corners(MqCornerScale.Small)

@PreviewTest
@MqPreviews
@Composable
fun CornersNormalScreenshot() = Corners(MqCornerScale.Normal)

@PreviewTest
@MqPreviews
@Composable
fun CornersLargeScreenshot() = Corners(MqCornerScale.Large)

private val CoverWidth = 112.dp
