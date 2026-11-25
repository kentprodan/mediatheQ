package io.github.kentprodan.mediatheq.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.ts.TsExtractor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoContainersTest {
    @Test
    fun `the MKV and TS extractors are replaced, the others kept`() {
        val default =
            androidx.media3.extractor
                .DefaultExtractorsFactory()
                .createExtractors()
        val ours = VideoContainerExtractorsFactory(StyledSubtitles()).createExtractors()

        assertEquals(default.size, ours.size)
        assertEquals(1, ours.count { it is PgsTolerantMatroskaExtractor })
        assertEquals(1, ours.count { it is HdmvTsExtractor })
        assertEquals(
            0,
            ours.count {
                it is TsExtractor ||
                    (it is MatroskaExtractor && it !is PgsTolerantMatroskaExtractor)
            },
        )
    }
}
