package io.github.kentprodan.mediatheq.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews

/**
 * The launcher banner (ADR 0038: xhdpi 320 x 180 px, the name in the image). `res/drawable-xhdpi/tv_banner.png` is a
 * copy of this reference image: change the banner here, record, and copy it there.
 */
@PreviewTest
@Preview(device = "spec:width=160dp,height=90dp,dpi=320")
@Composable
fun TvBannerScreenshot() =
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.tv_app_icon_background),
            contentDescription = null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph only: the foreground layer zoomed past its transparent safe-zone margin.
            Image(
                painterResource(R.drawable.tv_app_icon_foreground),
                contentDescription = null,
                Modifier.size(36.dp).scale(2.2f),
            )
            Text(
                "mediatheQ",
                color = Color(0xFFF5F0FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
