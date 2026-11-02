package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Immutable

/** What the selection toolbar runs on the selected items (#123); albums, artists and playlists act by their tracks. */
enum class SelectionCommand { Play, PlayNext, AddToQueue, AddToPlaylist, Download, Favorite }

/**
 * The items picked in a media list (#123): selecting while not empty. A long press starts it with that item, taps then
 * toggle items, Back or Clear ends it.
 */
@Immutable
data class ListSelection<K>(
    val keys: Set<K> = emptySet(),
) {
    val isActive: Boolean get() = keys.isNotEmpty()

    val size: Int get() = keys.size

    operator fun contains(key: K): Boolean = key in keys

    /** Adds [key], or removes it when selected; removing the last one ends selecting. */
    fun toggle(key: K): ListSelection<K> = ListSelection(if (key in keys) keys - key else keys + key)

    fun selectAll(all: Collection<K>): ListSelection<K> = ListSelection(all.toSet())

    /** Keeps only what is still in the list (items can disappear while selected). */
    fun retain(present: Collection<K>): ListSelection<K> = ListSelection(keys intersect present.toSet())

    /** [items] whose key is selected, in [items]' order. */
    fun <T> pick(
        items: List<T>,
        key: (T) -> K,
    ): List<T> = items.filter { key(it) in keys }
}
