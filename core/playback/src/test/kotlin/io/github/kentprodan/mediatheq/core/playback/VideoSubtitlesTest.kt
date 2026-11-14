package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.SubtitleDelivery
import io.github.kentprodan.mediatheq.core.model.SubtitleMethod
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.net.Uri in the side-loaded configurations.
@RunWith(RobolectricTestRunner::class)
class VideoSubtitlesTest {
    private val embeddedEng = SubtitleDelivery(2, SubtitleMethod.Embed, codec = "subrip", language = "eng")
    private val embeddedEng2 = SubtitleDelivery(3, SubtitleMethod.Embed, codec = "subrip", language = "eng")
    private val external =
        SubtitleDelivery(
            5,
            SubtitleMethod.External,
            "https://nas/Videos/m1/Subtitles/5/0/Stream.subrip",
            "subrip",
            "rum",
        )
    private val deliveries = listOf(embeddedEng, embeddedEng2, external)
    private val groups =
        listOf(TextGroup("3", "eng"), TextGroup("4", "eng"), TextGroup(sideLoadedId(5), "rum"))

    @Test
    fun `an external file is found by its id`() {
        assertThat(pickTextGroup(deliveries, 5, groups)).isEqualTo(2)
    }

    @Test
    fun `an embedded subtitle is found by its place among the embedded ones, the same language twice included`() {
        assertThat(pickTextGroup(deliveries, 2, groups)).isEqualTo(0)
        assertThat(pickTextGroup(deliveries, 3, groups)).isEqualTo(1)
    }

    @Test
    fun `an embedded subtitle without a language is taken at its place`() {
        val noLanguage = listOf(SubtitleDelivery(2, SubtitleMethod.Embed))

        assertThat(pickTextGroup(noLanguage, 2, listOf(TextGroup("3", null)))).isEqualTo(0)
    }

    @Test
    fun `none chosen, burned in or not found shows no text`() {
        assertThat(pickTextGroup(deliveries, null, groups)).isNull()
        assertThat(pickTextGroup(listOf(SubtitleDelivery(2, SubtitleMethod.Encode)), 2, groups)).isNull()
        assertThat(pickTextGroup(deliveries, 9, groups)).isNull()
    }

    @Test
    fun `a subtitle inside a conversion's playlist is found by language`() {
        val hls = listOf(SubtitleDelivery(2, SubtitleMethod.Hls, language = "rum"))

        assertThat(pickTextGroup(hls, 2, listOf(TextGroup("x", "eng"), TextGroup("y", "ron")))).isEqualTo(1)
    }

    @Test
    fun `external files are side-loaded with their format and language, the chosen one as default`() {
        val configurations = sideLoadedSubtitles(deliveries, chosen = 5)

        assertThat(configurations).hasSize(1)
        val configuration = configurations.single()
        assertThat(configuration.id).isEqualTo("sub:5")
        assertThat(configuration.mimeType).isEqualTo("application/x-subrip")
        assertThat(configuration.language).isEqualTo("rum")
        assertThat(configuration.selectionFlags).isEqualTo(C.SELECTION_FLAG_DEFAULT)
        assertThat(sideLoadedSubtitles(deliveries, chosen = null).single().selectionFlags).isEqualTo(0)
    }
}
