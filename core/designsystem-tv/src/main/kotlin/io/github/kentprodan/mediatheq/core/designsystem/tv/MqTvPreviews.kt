package io.github.kentprodan.mediatheq.core.designsystem.tv

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.ui.tooling.preview.Preview

/**
 * TV previews and screenshot variants (ADR 0037): a 1080p TV, dark, at font scale 1.0 and 1.3 (the phone's 2.0 breaks
 * every 10-foot layout).
 */
@Preview(name = "TV", device = TV_1080P, uiMode = TV_UI_MODE, showBackground = true, backgroundColor = TV_BACKGROUND)
@Preview(
    name = "TV large font",
    device = TV_1080P,
    uiMode = TV_UI_MODE,
    showBackground = true,
    backgroundColor = TV_BACKGROUND,
    fontScale = 1.3f,
)
annotation class MqTvPreviews

/** 1920 x 1080 px at 320 dpi: 960 x 540 dp, what a 1080p TV reports. */
private const val TV_1080P = "spec:width=960dp,height=540dp,dpi=320"
private const val TV_UI_MODE = UI_MODE_TYPE_TELEVISION or UI_MODE_NIGHT_YES

// The Iris dark background, so previews look like the app.
private const val TV_BACKGROUND = 0xFF141218
