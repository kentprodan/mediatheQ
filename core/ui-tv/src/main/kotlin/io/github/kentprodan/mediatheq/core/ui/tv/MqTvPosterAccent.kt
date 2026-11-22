package io.github.kentprodan.mediatheq.core.ui.tv

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvAccentTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.posterSeedColor
import io.github.kentprodan.mediatheq.core.model.Artwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings -> Display -> Accent from the poster (#517); the TV shell provides it. */
val LocalTvPosterAccent = staticCompositionLocalOf { false }

/** The seed previews and screenshots use instead of a loaded poster (layoutlib loads no images). */
val LocalPreviewPosterSeed = staticCompositionLocalOf<Int?> { null }

/**
 * A detail page (#517) in the colors of its [poster] (a movie's or series' poster, an artist's photo, an album's cover)
 * while Accent from the poster is on; the app's colors until the poster is read, without one, or with the setting off.
 */
@Composable
fun MqTvPosterAccent(
    poster: Artwork?,
    content: @Composable () -> Unit,
) {
    val seed = if (LocalTvPosterAccent.current) rememberPosterSeed(poster) else null
    MqTvAccentTheme(seed, content)
}

/** The accent of [poster], read once from a small copy and kept by its tag; `null` while it loads or without a color. */
@Composable
private fun rememberPosterSeed(poster: Artwork?): Int? {
    if (LocalInspectionMode.current) return LocalPreviewPosterSeed.current
    val context = LocalContext.current
    val key = poster?.let { "${it.itemId}/${it.tag}" }
    val cached = key?.let(seeds::get)
    var seed by remember(key) { mutableStateOf(cached?.takeIf { it != NO_SEED }) }
    LaunchedEffect(key) {
        if (key == null || cached != null) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(poster)
                .size(SEED_IMAGE_SIZE_PX)
                // Pixels cannot be read from hardware bitmaps.
                .allowHardware(false)
                .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        seed =
            bitmap
                ?.let {
                    withContext(
                        Dispatchers.Default,
                    ) { posterSeedColor(it.pixels()) }
                }.also { if (bitmap != null) seeds.put(key, it ?: NO_SEED) } // a failed load is tried again next time
    }
    return seed
}

private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

// Back to a page seen before: its accent at once, no second read; a picture without a color is kept as NO_SEED.
private val seeds = LruCache<String, Int>(64)

// A transparent color: never a seed (seeds are opaque).
private const val NO_SEED = 0

private const val SEED_IMAGE_SIZE_PX = 128
