package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.BaseItemDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.MediaStreamDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UserItemDataDto
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class LibraryMappersTest {
    private val server = ServerId("s")

    @Test
    fun `ticks are 100 ns`() {
        assertThat(ticksToDuration(2_416_326_760)).isEqualTo(241_632.milliseconds)
        assertThat(ticksToDuration(null)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `spatial formats map, everything else is plain audio`() {
        fun spatial(value: String?) =
            MediaStreamDto(codec = "eac3", audioSpatialFormat = value).toAudioFormat()?.spatialFormat
        assertThat(spatial("DolbyAtmos")).isEqualTo(SpatialFormat.DolbyAtmos)
        assertThat(spatial("DTSX")).isEqualTo(SpatialFormat.DtsX)
        assertThat(spatial("None")).isNull()
        assertThat(spatial(null)).isNull()
    }

    @Test
    fun `a stream without codec has no format`() {
        assertThat(MediaStreamDto(type = "Audio").toAudioFormat()).isNull()
    }

    @Test
    fun `a track without streams still maps, without a format`() {
        val track = BaseItemDto(id = "t1", name = "T").toTrack(server)

        assertThat(track?.audioFormat).isNull()
        assertThat(track?.sortName).isEqualTo("T")
    }

    @Test
    fun `items need an id and a name`() {
        assertThat(BaseItemDto(name = "x").toAlbum(server)).isNull()
        assertThat(BaseItemDto(id = "a1").toAlbum(server)).isNull()
        assertThat(BaseItemDto(id = "a:1", name = "x").toPlaylist(server)).isNull()
        assertThat(BaseItemDto(id = "", name = "x").toArtist(server)).isNull()
    }

    @Test
    fun `user data maps, with the last played time`() {
        val dto =
            BaseItemDto(
                id = "t1",
                name = "T",
                userData = UserItemDataDto(true, 10.0, 3, "2026-10-16T00:25:09.1655336Z", true),
            )

        assertThat(dto.toTrack(server)?.userData).isEqualTo(
            UserData(true, 10.0, 3, Instant.parse("2026-10-16T00:25:09.1655336Z"), true),
        )
    }

    @Test
    fun `an unreadable date is dropped, not fatal`() {
        assertThat(BaseItemDto(id = "t1", name = "T", dateCreated = "yesterday").toTrack(server)?.dateAdded).isNull()
    }

    @Test
    fun `a playlist entry without its own entry id is dropped`() {
        assertThat(BaseItemDto(id = "t1").toPlaylistEntry(server)).isNull()
        assertThat(BaseItemDto(id = "t1", playlistItemId = "e9").toPlaylistEntry(server)?.entryId).isEqualTo("e9")
        assertThat(BaseItemDto().toPlaylistEntry(server)).isNull()
    }
}
