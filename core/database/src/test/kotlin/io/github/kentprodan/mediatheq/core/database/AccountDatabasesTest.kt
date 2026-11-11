package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AccountDatabasesTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun file(name: String) =
        context.getDatabasePath(name).apply {
            parentFile?.mkdirs()
            writeText("db")
        }

    @Test
    fun `the default database opens until another one is selected, then at the next start`() {
        val first = SharedPreferencesAccountDatabases(context)
        assertThat(first.open).isEqualTo("mediatheq.db")

        val name = first.newName()
        assertThat(first.select(name)).isTrue()

        assertThat(first.open).isEqualTo("mediatheq.db")
        assertThat(SharedPreferencesAccountDatabases(context).open).isEqualTo(name)
    }

    @Test
    fun `only account databases are listed and deleted, never the open one`() {
        val databases = SharedPreferencesAccountDatabases(context)
        val other = databases.newName()
        listOf("mediatheq.db", other, "exoplayer_internal.db").forEach(::file)

        assertThat(databases.names()).containsExactly("mediatheq.db", other)

        databases.delete("mediatheq.db")
        databases.delete("exoplayer_internal.db")
        databases.delete(other)

        assertThat(context.databaseList().toList()).containsAtLeast("mediatheq.db", "exoplayer_internal.db")
        assertThat(context.databaseList().toList()).doesNotContain(other)
    }
}
