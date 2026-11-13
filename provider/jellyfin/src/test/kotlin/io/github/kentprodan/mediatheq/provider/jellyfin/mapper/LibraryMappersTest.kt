package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.BaseItemDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.MediaStreamDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.NameIdPairDto
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
    fun `R14 a rating of 0 maps to unrated`() {
        val dto = BaseItemDto(id = "t1", name = "T", userData = UserItemDataDto(rating = 0.0))

        assertThat(dto.toTrack(server)?.userData?.rating).isNull()
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

    @Test
    fun `a video's resume position and watched share come from its user data`() {
        val data = UserItemDataDto(played = false, playbackPositionTicks = 12_000_000_000, playedPercentage = 20.5)

        val movie = BaseItemDto(id = "m1", name = "M", userData = data).toVideo(server, VideoKind.Movie)

        assertThat(movie?.userData?.playbackPosition).isEqualTo(1_200_000.milliseconds)
        assertThat(movie?.userData?.playedPercentage).isEqualTo(20.5)
        assertThat(BaseItemDto(id = "m2", name = "M").toVideo(server, VideoKind.Movie)?.userData)
            .isEqualTo(UserData())
    }

    @Test
    fun `a movie without streams or series fields still maps`() {
        val movie = BaseItemDto(id = "m1", name = "M", seriesId = "x", indexNumber = 3).toVideo(server, VideoKind.Movie)

        assertThat(movie?.format).isNull()
        assertThat(movie?.streams).isEmpty()
        assertThat(movie?.seriesId).isNull()
        assertThat(movie?.episodeNumber).isNull()
    }

    @Test
    fun `an episode or season without its series is dropped`() {
        assertThat(BaseItemDto(id = "e1", name = "E").toVideo(server, VideoKind.Episode)).isNull()
        assertThat(BaseItemDto(id = "s1", name = "S").toSeason(server)).isNull()
    }

    @Test
    fun `a season without its own image shows the series poster`() {
        val season = BaseItemDto(id = "s1", name = "S", seriesId = "x", seriesPrimaryImageTag = "t").toSeason(server)

        assertThat(season?.artwork).isEqualTo(Artwork("x", "t"))
    }

    @Test
    fun `video ranges map, unknown is SDR`() {
        fun range(value: String?) = MediaStreamDto(codec = "hevc", videoRangeType = value).toVideoFormat()?.range
        assertThat(range("DOVIWithHLG")).isEqualTo(VideoRange.DolbyVision)
        assertThat(range("HDR10Plus")).isEqualTo(VideoRange.Hdr10Plus)
        assertThat(range("HDR10")).isEqualTo(VideoRange.Hdr10)
        assertThat(range("HLG")).isEqualTo(VideoRange.Hlg)
        assertThat(range("Unknown")).isEqualTo(VideoRange.Sdr)
        assertThat(range(null)).isEqualTo(VideoRange.Sdr)
    }

    @Test
    fun `only audio and subtitle streams with an index are kept, und is no language`() {
        assertThat(MediaStreamDto(type = "Video", index = 0).toStreamInfo()).isNull()
        assertThat(MediaStreamDto(type = "Audio").toStreamInfo()).isNull()
        assertThat(MediaStreamDto(type = "Subtitle", index = 2, language = "und").toStreamInfo()?.language).isNull()
    }

    @Test
    fun `series genres are names, each once`() {
        val genres = listOf(NameIdPairDto("g1", "Drama"), NameIdPairDto("g2", "Drama"), NameIdPairDto("g3", " "))

        val series = BaseItemDto(id = "s1", name = "S", genreItems = genres).toSeries(server)

        assertThat(series?.genres).containsExactly("Drama")
    }
}
