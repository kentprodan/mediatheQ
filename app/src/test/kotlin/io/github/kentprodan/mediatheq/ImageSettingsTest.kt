package io.github.kentprodan.mediatheq

import coil3.request.CachePolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ImageSettingsTest {
    private val files = File("/data/files")
    private val cache = File("/data/cache")

    @Test
    fun `the persistent cache lives in app storage, the other one in Coil's own cache folder`() {
        assertThat(imageCacheDir(persistent = true, files, cache)).isEqualTo(File(files, "coil3_disk_cache"))
        assertThat(imageCacheDir(persistent = false, files, cache)).isEqualTo(File(cache, "coil3_disk_cache"))
        assertThat(unusedImageCacheDir(persistent = true, files, cache)).isEqualTo(File(cache, "coil3_disk_cache"))
        assertThat(unusedImageCacheDir(persistent = false, files, cache)).isEqualTo(File(files, "coil3_disk_cache"))
    }

    @Test
    fun `Wi-Fi only images keep the network off only on a metered network`() {
        assertThat(imageNetworkPolicy(wifiOnly = true, metered = true)).isEqualTo(CachePolicy.DISABLED)
        assertThat(imageNetworkPolicy(wifiOnly = true, metered = false)).isEqualTo(CachePolicy.ENABLED)
        assertThat(imageNetworkPolicy(wifiOnly = false, metered = true)).isEqualTo(CachePolicy.ENABLED)
    }
}
