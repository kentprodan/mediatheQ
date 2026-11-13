package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class InputGuardTest {
    @Test
    fun `a whole file read once passes, reading it again past twice its size stops the decode`() {
        val guard = InputGuard(fileBytes = 1_000)

        repeat(20) { guard.read(100) }
        assertThat(guard.bytesRead).isEqualTo(2_000)

        assertThrows(EndlessInputException::class.java) { guard.read(1) }
    }

    @Test
    fun `every read through the source counts, also reading the file again from the start`() {
        val data = FakeDataSet().apply { setRandomData("file", 100) }
        val guard = InputGuard(fileBytes = 100)
        val source = GuardedDataSource(FakeDataSource(data), guard)
        val buffer = ByteArray(100)

        fun readAll() {
            source.open(DataSpec(Uri.parse("file")))
            while (source.read(buffer, 0, buffer.size) > 0) Unit
            source.close()
        }
        readAll()
        readAll()
        assertThat(guard.bytesRead).isEqualTo(200)

        assertThrows(EndlessInputException::class.java) { readAll() }
    }
}
