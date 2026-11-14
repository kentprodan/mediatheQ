package io.github.kentprodan.mediatheq.core.ui

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
 * The video picture of the session's player (ADR 0049): a `SurfaceView` fitted to the video's aspect ratio on black,
 * with a black shutter until the first frame. The only place that opts into Media3's unstable Compose UI (checked at
 * every Media3 bump, like `LazyStreamMediaSource`).
 */
@OptIn(UnstableApi::class)
@Composable
fun MqVideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black)) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
        )
    }
}
