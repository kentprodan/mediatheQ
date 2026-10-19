package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.ColorRoleGroup
import io.github.kentprodan.mediatheq.core.designsystem.preview.ColorRolesShowcase
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.ShapesShowcase
import io.github.kentprodan.mediatheq.core.designsystem.preview.TypeScaleShowcase
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

// One test per showcase section so each fits the Pixel 9 viewport, also at fontScale 2.0.

@PreviewTest
@MqPreviews
@Composable
fun AccentColorsScreenshot() {
    MediatheqTheme { Surface { ColorRolesShowcase(ColorRoleGroup.Accent) } }
}

@PreviewTest
@MqPreviews
@Composable
fun SurfaceColorsScreenshot() {
    MediatheqTheme { Surface { ColorRolesShowcase(ColorRoleGroup.Surface) } }
}

@PreviewTest
@MqPreviews
@Composable
fun FixedColorsScreenshot() {
    MediatheqTheme { Surface { ColorRolesShowcase(ColorRoleGroup.Fixed) } }
}

@PreviewTest
@MqPreviews
@Composable
fun TypeScaleScreenshot() {
    MediatheqTheme { Surface { TypeScaleShowcase(emphasized = false) } }
}

@PreviewTest
@MqPreviews
@Composable
fun EmphasizedTypeScaleScreenshot() {
    MediatheqTheme { Surface { TypeScaleShowcase(emphasized = true) } }
}

@PreviewTest
@MqPreviews
@Composable
fun ShapesScreenshot() {
    MediatheqTheme { Surface { ShapesShowcase() } }
}
