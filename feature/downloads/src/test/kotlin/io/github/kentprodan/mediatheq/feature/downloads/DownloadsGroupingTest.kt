package io.github.kentprodan.mediatheq.feature.downloads

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
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
    fun `an album downloaded both by hand and as a favorite shows once, as a manual one`() {
        val autoA = DownloadOrigin.Album(albumA.id, auto = true)
        val autoB = DownloadOrigin.Album(albumB.id, auto = true)
        val content =
            downloadsContent(
                listOf(
                    done(track("t1"), albumA, autoA),
                    done(track("t2"), autoA),
                    done(track("t3", "B"), autoB),
                ),
                emptyMap(),
                emptyMap(),
                freeBytes = 0,
            )

        val (a, b) = content.albums.sortedBy { it.album.id.itemId }
        assertThat(a.tracks.map { it.id.itemId }).containsExactly("t1", "t2")
        assertThat(a.auto).isFalse()
        assertThat(b.auto).isTrue()
    }

    @Test
    fun `favorite tracks show under songs`() {
        val favorite = track("t9")
        val content = downloadsContent(listOf(done(favorite, DownloadOrigin.Favorite)), emptyMap(), emptyMap(), 0)

        assertThat(content.songs.single().track).isEqualTo(favorite)
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

    @Test
    fun `the summary splits finished downloads into manual and automatic ones`() {
        val favorite = DownloadOrigin.Favorite
        val autoA = DownloadOrigin.Album(albumA.id, auto = true)
        val content =
            downloadsContent(
                listOf(
                    done(track("t1"), albumA, autoA, bytes = 10),
                    done(track("t2"), autoA, bytes = 20),
                    done(track("t3"), favorite, bytes = 30),
                    done(track("t4"), bytes = 5),
                    TrackDownload(track("t5"), DownloadState.Failed, bytes = 99, origins = setOf(albumA)),
                ),
                emptyMap(),
                emptyMap(),
                freeBytes = 0,
            )

        // A track kept by hand too counts as manual; one with no origin (from before #168) as well; unfinished ones not.
        assertThat(content.totals.manual).isEqualTo(OfflineUsage(2, 15))
        assertThat(content.totals.auto).isEqualTo(OfflineUsage(2, 50))
        assertThat(content.totals.bytes).isEqualTo(65)
    }

    @Test
    fun `the filter keeps what matches every word, ignoring case and diacritics`() {
        val moșoi = Track(id("t1"), "Doină", listOf(Artist(id("ar"), "Moșoi")), 2.minutes, id("A"), "Album A")
        val other = track("t2", "B")
        val content =
            downloadsContent(
                listOf(done(moșoi, albumA, DownloadOrigin.Single), done(other, albumB, playlist)),
                mapOf(
                    albumA.id to Album(albumA.id, "Album A", emptyList()),
                    albumB.id to Album(albumB.id, "Album B", emptyList()),
                ),
                mapOf(playlist.id to Playlist(playlist.id, "Mix")),
                freeBytes = 0,
            )

        val filtered = filterDownloads(content, "MOSOI doina")

        assertThat(filtered.albums.map { it.album.title }).containsExactly("Album A")
        assertThat(filtered.playlists).isEmpty()
        assertThat(filtered.songs.map { it.track }).containsExactly(moșoi)
        assertThat(filterDownloads(content, "mix").playlists.map { it.playlist.name }).containsExactly("Mix")
        assertThat(filterDownloads(content, "  ")).isEqualTo(content)
    }

    @Test
    fun `the user's pause wins over waiting, never over a failure or a running download`() {
        assertThat(queueStatus(DownloadState.WaitingForWifi, paused = true)).isEqualTo(QueueStatus.Paused)
        assertThat(queueStatus(DownloadState.Queued, paused = true)).isEqualTo(QueueStatus.Paused)
        assertThat(queueStatus(DownloadState.WaitingForWifi, paused = false)).isEqualTo(QueueStatus.WaitingForWifi)
        assertThat(queueStatus(DownloadState.Queued, paused = false)).isEqualTo(QueueStatus.Queued)
        assertThat(queueStatus(DownloadState.Failed, paused = true)).isEqualTo(QueueStatus.Failed)
        assertThat(queueStatus(DownloadState.Downloading(0.5f), paused = true)).isEqualTo(QueueStatus.Downloading)
    }
}
