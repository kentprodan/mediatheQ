package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListSelectionTest {
    @Test
    fun `toggle adds and removes, and removing the last one ends selecting`() {
        val one = ListSelection<String>().toggle("a")
        assertThat(one.isActive).isTrue()
        assertThat(one.toggle("b").keys).containsExactly("a", "b")
        assertThat(one.toggle("a").isActive).isFalse()
    }

    @Test
    fun `select all, retain and clear`() {
        val all = ListSelection<String>().selectAll(listOf("a", "b", "c"))
        assertThat(all.size).isEqualTo(3)
        assertThat(all.retain(listOf("b", "z")).keys).containsExactly("b")
        assertThat(ListSelection<String>().isActive).isFalse()
    }

    @Test
    fun `pick keeps the list order`() {
        val selection = ListSelection(setOf("c", "a"))
        assertThat(selection.pick(listOf("a", "b", "c")) { it }).containsExactly("a", "c").inOrder()
    }
}
