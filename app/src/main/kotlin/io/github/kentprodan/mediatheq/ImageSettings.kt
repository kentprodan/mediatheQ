package io.github.kentprodan.mediatheq

import coil3.request.CachePolicy
import java.io.File

/**
 * Where covers are cached (#144): app storage, which the system never clears, or the cache folder it may clear (Coil's
 * own default place, so the covers cached before #144 stay).
 */
internal fun imageCacheDir(
    persistent: Boolean,
    filesDir: File,
    cacheDir: File,
): File = File(if (persistent) filesDir else cacheDir, IMAGE_CACHE_DIR)

/** The image cache folder not in use: deleted, so switching the setting clears the cache and leaves no copy behind. */
internal fun unusedImageCacheDir(
    persistent: Boolean,
    filesDir: File,
    cacheDir: File,
): File = imageCacheDir(!persistent, filesDir, cacheDir)

/** Wi-Fi only image downloads (#144): on a metered network only covers already cached show. */
internal fun imageNetworkPolicy(
    wifiOnly: Boolean,
    metered: Boolean,
): CachePolicy = if (wifiOnly && metered) CachePolicy.DISABLED else CachePolicy.ENABLED

private const val IMAGE_CACHE_DIR = "coil3_disk_cache"
