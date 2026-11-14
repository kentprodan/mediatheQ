package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import org.junit.Test

class TrackChoicesTest {
    private val streams =
        listOf(
            MediaStreamInfo(1, StreamType.Audio, "eac3", "eng", channels = 6, isDefault = true),
            MediaStreamInfo(2, StreamType.Audio, "aac", "rum", channels = 2),
            MediaStreamInfo(3, StreamType.Subtitle, "subrip", "eng", isForced = true),
            MediaStreamInfo(4, StreamType.Subtitle, "subrip", "eng"),
            MediaStreamInfo(5, StreamType.Subtitle, "subrip", "rum", isExternal = true),
        )
    private val defaults = TrackDefaults(audioLanguage = "en", subtitleMode = SubtitleMode.ForcedOnly)

    @Test
    fun `the item's own indexes win while they exist`() {
        val item = TrackChoice(audioIndex = 2, subtitleIndex = 5)

        assertThat(resolveChoice(streams, item, null, defaults)).isEqualTo(ResolvedTracks(2, 5))
    }

    @Test
    fun `stale item indexes fall back to the season, then to the defaults`() {
        val stale = TrackChoice(audioIndex = 9, subtitleIndex = 9)
        val season = TrackChoice(audioLanguage = "ron", subtitleLanguage = "ro")

        assertThat(resolveChoice(streams, stale, season, defaults)).isEqualTo(ResolvedTracks(2, 5))
        assertThat(resolveChoice(streams, stale, null, defaults)).isEqualTo(ResolvedTracks(1, 3))
    }

    @Test
    fun `a season choice finds its language in another episode, the full subtitle before a forced one`() {
        val season = TrackChoice(subtitleLanguage = "eng")

        assertThat(resolveChoice(streams, null, season, defaults).subtitleIndex).isEqualTo(4)
    }

    @Test
    fun `a season language the episode does not have gives no subtitle`() {
        val season = TrackChoice(subtitleLanguage = "fre")

        assertThat(resolveChoice(streams, null, season, defaults).subtitleIndex).isNull()
    }

    @Test
    fun `subtitles turned off stay off, for the item and for the season`() {
        assertThat(resolveChoice(streams, TrackChoice(subtitleOff = true), null, defaults).subtitleIndex).isNull()
        assertThat(resolveChoice(streams, null, TrackChoice(subtitleOff = true), defaults).subtitleIndex).isNull()
    }

    @Test
    fun `without a choice the defaults decide, forced only in the audio language, a language, or none`() {
        assertThat(resolveChoice(streams, null, null, defaults)).isEqualTo(ResolvedTracks(1, 3))
        assertThat(
            resolveChoice(
                streams,
                null,
                null,
                TrackDefaults(subtitleMode = SubtitleMode.Language, subtitleLanguage = "ro"),
            ),
        ).isEqualTo(ResolvedTracks(null, 5))
        assertThat(
            resolveChoice(streams, null, null, TrackDefaults(subtitleMode = SubtitleMode.Off)).subtitleIndex,
        ).isNull()
    }

    @Test
    fun `two-letter and both three-letter codes name the same language`() {
        assertThat(sameLanguage("ro", "rum")).isTrue()
        assertThat(sameLanguage("ron", "rum")).isTrue()
        assertThat(sameLanguage("en", "eng")).isTrue()
        assertThat(sameLanguage("de", "ger")).isTrue()
        assertThat(sameLanguage("en", "fre")).isFalse()
        assertThat(sameLanguage(null, "eng")).isFalse()
    }
}
