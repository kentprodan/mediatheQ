package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.ColorRolesShowcase
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.ShapesShowcase
import io.github.kentprodan.mediatheq.core.designsystem.preview.TypeScaleShowcase
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

// One test per showcase section so each fits the Pixel 9 viewport.

@PreviewTest
@MqPreviews
@Composable
fun ColorRolesScreenshot() {
    MediatheqTheme { Surface { ColorRolesShowcase() } }
}

@PreviewTest
@MqPreviews
@Composable
fun TypeScaleScreenshot() {
    MediatheqTheme { Surface { TypeScaleShowcase() } }
}

@PreviewTest
@MqPreviews
@Composable
fun ShapesScreenshot() {
    MediatheqTheme { Surface { ShapesShowcase() } }
}
