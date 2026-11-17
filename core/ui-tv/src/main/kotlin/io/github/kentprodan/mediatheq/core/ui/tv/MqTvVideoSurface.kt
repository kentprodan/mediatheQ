package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

/**
 * The video picture on TV (#429, ADR 0049): the session's player in a `SurfaceView` (HDR and Dolby Vision need one),
 * scaled by [scale] (Fit by default; Fill crops, Stretch fills), black until the first frame. Like the phone's
 * MqVideoSurface, the only TV place that opts into Media3's unstable Compose UI.
 */
@OptIn(UnstableApi::class)
@Composable
fun MqTvVideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Fit,
) {
    Box(modifier.background(Color.Black)) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            contentScale = scale,
            shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
        )
    }
}
