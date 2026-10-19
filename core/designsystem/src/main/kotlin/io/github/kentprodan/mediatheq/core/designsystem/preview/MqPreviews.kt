package io.github.kentprodan.mediatheq.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/** The shared multipreview for components and screens: light/dark x fontScale 1.0/2.0 on Pixel 9. */
@Preview(name = "Light", device = Devices.PIXEL_9, showBackground = true)
@Preview(name = "Dark", device = Devices.PIXEL_9, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light 2x", device = Devices.PIXEL_9, showBackground = true, fontScale = 2f)
@Preview(
    name = "Dark 2x",
    device = Devices.PIXEL_9,
    showBackground = true,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class MqPreviews
