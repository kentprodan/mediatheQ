package io.github.kentprodan.mediatheq.core.designsystem.theme

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
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

/** The light and dark player schemes of one cover, as `SchemeContent` and as the more saturated `SchemeVibrant`. */
class CoverColors(
    val light: ColorScheme,
    val dark: ColorScheme,
    val vibrantLight: ColorScheme,
    val vibrantDark: ColorScheme,
) {
    fun scheme(
        isDark: Boolean,
        vibrant: Boolean,
    ): ColorScheme =
        when {
            vibrant && isDark -> vibrantDark
            vibrant -> vibrantLight
            isDark -> dark
            else -> light
        }
}

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
        CoverColors(
            coverColorScheme(seed, isDark = false),
            coverColorScheme(seed, isDark = true),
            coverColorScheme(seed, isDark = false, vibrant = true),
            coverColorScheme(seed, isDark = true, vibrant = true),
        )
    }

/**
 * The player scheme for the cover [model] (anything the app's image loader takes) identified by [key], per the theme's
 * player options ([playerColorScheme]); `null` (the app theme) without a cover, when it has no usable color, or with
 * cover colors off. While a new cover loads, the previous one's scheme stays, so colors blend from cover to cover
 * instead of passing through the app theme.
 */
@Composable
fun rememberCoverColorScheme(
    key: String?,
    model: Any?,
    cache: CoverColorCache,
): ColorScheme? {
    val theme = LocalMqTheme.current
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
    // Kept across recompositions: schemes compare by identity, and a new one would restart the blend.
    val options = theme.options
    return remember(colors, theme.isDark, theme.isBlack, options.playerColors, options.ignoreSurfaces) {
        playerColorScheme(colors, theme)
    }
}

/**
 * Applies [colorScheme] (or the surrounding theme's when `null`) to [content]; a change blends every role with the
 * theme's effects spec instead of snapping. With "ignore background and surfaces" those roles stay the surrounding
 * theme's.
 */
@Composable
fun MqCoverTheme(
    colorScheme: ColorScheme?,
    content: @Composable () -> Unit,
) {
    val app = MaterialTheme.colorScheme
    val options = LocalMqTheme.current.options
    val shown =
        animateColorScheme(
            colorScheme ?: app,
            MaterialTheme.motionScheme.defaultEffectsSpec(),
            instant = options.instantColors,
        )
    // Merged after the blend: the app's own roles may be blending too (a theme change).
    MaterialTheme(colorScheme = if (options.ignoreSurfaces) shown.withSurfacesOf(app) else shown, content = content)
}

/** [target], with a change blending every role with [spec] (snapping when [instant]). */
@Composable
internal fun animateColorScheme(
    target: ColorScheme,
    spec: FiniteAnimationSpec<Float>,
    instant: Boolean,
): ColorScheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val shown = from.lerp(to, progress.value)
    LaunchedEffect(target, instant) {
        if (target == to) return@LaunchedEffect
        // Start from what is on screen, also when a change interrupts another.
        from = shown
        to = target
        progress.snapTo(0f)
        if (instant) progress.snapTo(1f) else progress.animateTo(1f, spec)
    }
    // At rest the target itself, not a fresh copy: schemes compare by identity downstream.
    return when {
        instant -> target
        progress.value == 1f -> to
        else -> shown
    }
}

private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

private const val SEED_IMAGE_SIZE_PX = 128
