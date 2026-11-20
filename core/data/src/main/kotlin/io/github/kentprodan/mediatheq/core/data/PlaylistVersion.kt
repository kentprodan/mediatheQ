package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.entity.PendingPlaylistEditEntity
import io.github.kentprodan.mediatheq.core.model.PlaylistChange

/** A playlist as a name and its track ids in order (#417); on Jellyfin 12.2 entry ids are track ids (R13). */
internal data class PlaylistVersion(
    val name: String,
    val trackIds: List<String>,
)

internal const val EDIT_ADD = "Add"
internal const val EDIT_REMOVE = "Remove"
internal const val EDIT_MOVE = "Move"
internal const val EDIT_RENAME = "Rename"
internal const val ID_SEPARATOR = ","

internal fun String?.ids(): List<String> = this?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

/**
 * What [edit] makes of this version, as the server would (R13): Remove drops every copy of the track, Move moves the
 * first copy, a position past the end moves to the end.
 */
internal fun PlaylistVersion.apply(edit: PendingPlaylistEditEntity): PlaylistVersion =
    when (edit.kind) {
        EDIT_ADD -> {
            copy(trackIds = trackIds + edit.trackIds.ids())
        }

        EDIT_REMOVE -> {
            val gone = edit.entryIds.ids().toSet()
            copy(trackIds = trackIds.filterNot { it in gone })
        }

        EDIT_MOVE -> {
            val entry = edit.entryIds.ids().firstOrNull()
            val from = trackIds.indexOf(entry)
            if (entry == null || from < 0) {
                this
            } else {
                val rest = trackIds.toMutableList().apply { removeAt(from) }
                rest.add((edit.position ?: 0).coerceIn(0, rest.size), entry)
                copy(trackIds = rest)
            }
        }

        EDIT_RENAME -> {
            edit.name?.let { copy(name = it) } ?: this
        }

        else -> {
            this
        }
    }

/** The phone's version: the waiting [edits] on top of the version they were made on. */
internal fun PlaylistVersion.applyAll(edits: List<PendingPlaylistEditEntity>): PlaylistVersion =
    edits.fold(this) { version, edit -> version.apply(edit) }

/** How [to] differs from this version; `null` when they are the same. */
internal fun PlaylistVersion.changeTo(to: PlaylistVersion): PlaylistChange? {
    if (this == to) return null
    val before = trackIds.groupingBy { it }.eachCount()
    val after = to.trackIds.groupingBy { it }.eachCount()
    val added = after.entries.sumOf { (id, n) -> (n - (before[id] ?: 0)).coerceAtLeast(0) }
    val removed = before.entries.sumOf { (id, n) -> (n - (after[id] ?: 0)).coerceAtLeast(0) }
    // Reordered: the tracks both keep (first copies) are in another order.
    val kept = to.trackIds.distinct().filter { it in before }
    val keptBefore = trackIds.distinct().filter { it in after }
    return PlaylistChange(
        added = added,
        removed = removed,
        reordered = kept != keptBefore,
        renamedTo = to.name.takeIf { it != name },
    )
}
