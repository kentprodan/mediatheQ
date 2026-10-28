package io.github.kentprodan.mediatheq.core.playback

import android.app.Application
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalCoverBytesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `R154 a kept cover is read from the device, so the boot card has it offline`() {
        val file = folder.newFile("s_al1_tag.jpg").apply { writeText("jpeg") }

        assertThat(localCoverBytes(Uri.fromFile(file))?.decodeToString()).isEqualTo("jpeg")
    }

    @Test
    fun `a server cover or a missing file is not read here`() {
        assertThat(localCoverBytes(Uri.parse("http://nas:8096/Items/al1/Images/Primary"))).isNull()
        assertThat(localCoverBytes(Uri.fromFile(folder.root.resolve("gone.jpg")))).isNull()
    }
}
