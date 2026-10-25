package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import org.junit.Test

class SocketMappersTest {
    private val serverId = ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val server =
        SignedInServer(
            ServerConnection("http://nas:8096/", "device-1", "token-1"),
            serverId,
            "11111111222233334444555555555555",
        )
    private val trackId = "6f3c2a1b0e9d8c7b6a5f4e3d2c1b0a99"

    private fun fixture(name: String) = javaClass.getResource("/jellyfin/socket/$name.json")!!.readText()

    private fun String.parse() = toSocketMessage(MediatheqJson, server)

    private fun String.userDataEvent() = ((parse() as SocketMessage.Event).event as LiveEvent.UserDataChanged)

    @Test
    fun `ForceKeepAlive gives the server timeout`() {
        assertThat(fixture("force_keep_alive").parse()).isEqualTo(SocketMessage.ForceKeepAlive(60))
    }

    @Test
    fun `UserDataChanged for our user maps each item, dashed user id or not`() {
        assertThat(fixture("user_data_changed").userDataEvent().changes).containsExactly(
            MediaId(serverId, trackId),
            UserData(isFavorite = true, rating = 8.0, playCount = 3, played = true),
        )
    }

    @Test
    fun `UserDataChanged of another user is ignored`() {
        val other = fixture("user_data_changed").replace("11111111-2222", "99999999-2222")

        assertThat(other.parse()).isNull()
    }

    @Test
    fun `dashed upper case item ids map to the library id form`() {
        val dashed = fixture("user_data_changed").replace(trackId, "6F3C2A1B-0E9D-8C7B-6A5F-4E3D2C1B0A99")

        assertThat(dashed.userDataEvent().changes.keys).containsExactly(MediaId(serverId, trackId))
    }

    @Test
    fun `R14 a rating of 0 arrives as unrated`() {
        val cleared = fixture("user_data_changed").replace("\"Rating\":8", "\"Rating\":0")

        assertThat(
            cleared
                .userDataEvent()
                .changes.values
                .single()
                .rating,
        ).isNull()
    }

    @Test
    fun `LibraryChanged is one event`() {
        assertThat(fixture("library_changed").parse()).isEqualTo(SocketMessage.Event(LiveEvent.LibraryChanged))
    }

    @Test
    fun `unknown types and unreadable frames are ignored`() {
        assertThat("""{"MessageType":"Sessions","Data":[]}""".parse()).isNull()
        assertThat("""{"MessageType":"KeepAlive"}""".parse()).isNull()
        assertThat("not json".parse()).isNull()
        assertThat("""{"MessageType":"ForceKeepAlive","Data":"soon"}""".parse()).isNull()
    }
}
