package io.github.kentprodan.mediatheq.core.navigation

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.serialization.json.Json
import org.junit.Test

class LibraryKeysTest {
    private val id = MediaId(ServerId("server-1"), "item:with:colons")

    @Test
    fun `keys with a MediaId survive saving and restoring`() {
        val album = AlbumKey(id)
        val artist = ArtistKey(id)
        val playlist = PlaylistKey(id)

        assertThat(Json.decodeFromString<AlbumKey>(Json.encodeToString(album))).isEqualTo(album)
        assertThat(Json.decodeFromString<ArtistKey>(Json.encodeToString(artist))).isEqualTo(artist)
        assertThat(Json.decodeFromString<PlaylistKey>(Json.encodeToString(playlist))).isEqualTo(playlist)
        assertThat(Json.encodeToString(album)).isEqualTo("""{"id":"server-1:item:with:colons"}""")
        val add = AddToPlaylistKey(listOf(id, id))
        assertThat(Json.decodeFromString<AddToPlaylistKey>(Json.encodeToString(add))).isEqualTo(add)
    }
}
