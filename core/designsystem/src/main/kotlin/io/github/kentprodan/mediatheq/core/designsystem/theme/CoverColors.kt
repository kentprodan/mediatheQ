package io.github.kentprodan.mediatheq.core.designsystem.theme

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The light and dark player schemes of one cover. */
class CoverColors(
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * Cover schemes by cover key (ADR 0017), so a track change on the same album or a recomposition never recomputes.
 * Failures are not kept: the next request tries again.
 */
class CoverColorCache(
    maxSize: Int = 32,
) {
    private val entries = LruCache<String, CoverColors>(maxSize)

    operator fun get(key: String): CoverColors? = entries[key]

    /** The cached colors for [key], or [compute]d ones (cached only when there are some). */
    suspend fun getOrCompute(
        key: String,
        compute: suspend () -> CoverColors?,
    ): CoverColors? = entries[key] ?: compute()?.also { entries.put(key, it) }
}

/** The colors of a cover from its pixels, or `null` when it has no usable color (the caller keeps Iris). */
fun coverColorsOf(pixels: IntArray): CoverColors? =
    coverSeedColor(pixels)?.let { seed ->
        CoverColors(coverColorScheme(seed, isDark = false), coverColorScheme(seed, isDark = true))
    }

/**
 * The player scheme for the cover [model] (anything the app's image loader takes) identified by [key]; `null` without a
 * cover or when it has no usable color. While a new cover loads, the previous one's scheme stays, so colors blend from
 * cover to cover instead of passing through Iris.
 */
@Composable
fun rememberCoverColorScheme(
    key: String?,
    model: Any?,
    cache: CoverColorCache,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme? {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<CoverColors?>(null) }
    LaunchedEffect(key) {
        if (key == null || model == null) {
            loaded = null
            return@LaunchedEffect
        }
        loaded =
            cache.getOrCompute(key) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(model)
                        .size(SEED_IMAGE_SIZE_PX)
                        // Pixels cannot be read from hardware bitmaps.
                        .allowHardware(false)
                        .build()
                val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                bitmap?.let { withContext(Dispatchers.Default) { coverColorsOf(it.pixels()) } }
            }
    }
    val colors = if (key == null) null else key.let { cache[it] } ?: loaded
    return colors?.let { if (darkTheme) it.dark else it.light }
}

/**
 * Applies [colorScheme] (or the surrounding theme's when `null`) to [content]; a change blends every role with the
 * theme's effects spec instead of snapping.
 */
@Composable
fun MqCoverTheme(
    colorScheme: ColorScheme?,
    content: @Composable () -> Unit,
) {
    val target = colorScheme ?: MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val shown = from.lerp(to, progress.value)
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // Start from what is on screen, also when a change interrupts another.
        from = shown
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, effects)
    }
    MaterialTheme(colorScheme = shown, content = content)
}

private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

private const val SEED_IMAGE_SIZE_PX = 128
