package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.PendingPlaylistEditEntity
import io.github.kentprodan.mediatheq.core.model.PlaylistChange
import org.junit.Test

class PlaylistVersionTest {
    private val base = PlaylistVersion("Road", listOf("a", "b", "c"))

    private fun edit(
        kind: String,
        trackIds: String? = null,
        entryIds: String? = null,
        position: Int? = null,
        name: String? = null,
    ) = PendingPlaylistEditEntity(
        serverId = "s",
        userId = "u",
        playlistId = "p",
        kind = kind,
        trackIds = trackIds,
        entryIds = entryIds,
        position = position,
        name = name,
    )

    @Test
    fun `waiting edits apply in order on the base`() {
        val phone =
            base.applyAll(
                listOf(
                    edit(EDIT_ADD, trackIds = "d,e"),
                    edit(EDIT_REMOVE, entryIds = "b"),
                    edit(EDIT_MOVE, entryIds = "e", position = 0),
                    edit(EDIT_RENAME, name = "Trip"),
                ),
            )

        assertThat(phone).isEqualTo(PlaylistVersion("Trip", listOf("e", "a", "c", "d")))
    }

    @Test
    fun `remove drops every copy and move takes the first, as the server does`() {
        val version = PlaylistVersion("Road", listOf("a", "b", "a", "c"))

        assertThat(version.apply(edit(EDIT_REMOVE, entryIds = "a")).trackIds).containsExactly("b", "c").inOrder()
        assertThat(version.apply(edit(EDIT_MOVE, entryIds = "a", position = 9)).trackIds)
            .containsExactly("b", "a", "c", "a")
            .inOrder()
    }

    @Test
    fun `an edit whose track is gone changes nothing`() {
        assertThat(base.apply(edit(EDIT_MOVE, entryIds = "x", position = 0))).isEqualTo(base)
        assertThat(base.apply(edit(EDIT_REMOVE, entryIds = "x"))).isEqualTo(base)
    }

    @Test
    fun `the same version is no change`() {
        assertThat(base.changeTo(base.copy())).isNull()
    }

    @Test
    fun `a change counts added and removed tracks and a new name`() {
        val server = PlaylistVersion("Trip", listOf("a", "c", "d", "e"))

        assertThat(base.changeTo(server)).isEqualTo(PlaylistChange(added = 2, removed = 1, renamedTo = "Trip"))
    }

    @Test
    fun `a change in order alone is a reorder`() {
        assertThat(base.changeTo(base.copy(trackIds = listOf("c", "a", "b"))))
            .isEqualTo(PlaylistChange(reordered = true))
    }

    @Test
    fun `removing one copy of a duplicate is not a reorder`() {
        val version = PlaylistVersion("Road", listOf("a", "b", "a"))

        assertThat(version.changeTo(version.copy(trackIds = listOf("a", "b"))))
            .isEqualTo(PlaylistChange(removed = 1))
    }
}
