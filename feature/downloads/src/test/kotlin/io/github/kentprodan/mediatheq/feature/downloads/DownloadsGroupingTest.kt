package io.github.kentprodan.mediatheq.feature.downloads

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DownloadsGroupingTest {
    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun track(
        id: String,
        album: String = "A",
    ) = Track(id(id), "Track $id", emptyList(), 2.minutes, albumId = id(album), albumTitle = "Album $album")

    private val albumA = DownloadOrigin.Album(id("A"))
    private val albumB = DownloadOrigin.Album(id("B"))
    private val playlist = DownloadOrigin.Playlist(id("p1"))

    private fun done(
        track: Track,
        vararg origins: DownloadOrigin,
        bytes: Long = 10,
    ) = TrackDownload(track, DownloadState.Completed, bytes = bytes, origins = origins.toSet())

    @Test
    fun `a track downloaded through an album and a playlist shows under both, and counts once in the totals`() {
        val shared = track("t1")
        val content =
            downloadsContent(
                listOf(done(shared, albumA, playlist)),
                mapOf(albumA.id to Album(albumA.id, "Album A", emptyList())),
                mapOf(playlist.id to Playlist(playlist.id, "Mix")),
                freeBytes = 0,
            )

        assertThat(content.albums.single().tracks).containsExactly(shared)
        assertThat(content.playlists.single().tracks).containsExactly(shared)
        assertThat(content.totals.bytes).isEqualTo(10)
        assertThat(content.totals.duration).isEqualTo(2.minutes)
    }

    @Test
    fun `sizes and durations add up per collection`() {
        val content =
            downloadsContent(
                listOf(done(track("t1"), albumA, bytes = 30), done(track("t2"), albumA, bytes = 20)),
                emptyMap(),
                emptyMap(),
                freeBytes = 0,
            )

        val album = content.albums.single()
        assertThat(album.bytes).isEqualTo(50)
        assertThat(album.duration).isEqualTo(4.minutes)
    }

    @Test
    fun `collections are sorted by name, and one gone from the library keeps the album title`() {
        val content =
            downloadsContent(
                listOf(done(track("t1", "B"), albumB), done(track("t2", "A"), albumA)),
                mapOf(albumB.id to Album(albumB.id, "Abba Gold", emptyList())),
                emptyMap(),
                freeBytes = 0,
            )

        assertThat(content.albums.map { it.album.title }).containsExactly("Abba Gold", "Album A").inOrder()
    }

    @Test
    fun `unfinished downloads are only in the downloading list`() {
        val waiting = TrackDownload(track("t1"), DownloadState.WaitingForWifi, origins = setOf(albumA))

        val content = downloadsContent(listOf(waiting), emptyMap(), emptyMap(), freeBytes = 5)

        assertThat(content.downloading).containsExactly(waiting)
        assertThat(content.albums).isEmpty()
        assertThat(content.totals).isEqualTo(DownloadTotals(0, 0.minutes, 5))
    }

    @Test
    fun `songs are the tracks downloaded on their own`() {
        val alone = track("t1")

        val content =
            downloadsContent(
                listOf(done(alone, DownloadOrigin.Single), done(track("t2"), albumA)),
                emptyMap(),
                emptyMap(),
                freeBytes = 0,
            )

        assertThat(content.songs.map { it.track }).containsExactly(alone)
    }
}
