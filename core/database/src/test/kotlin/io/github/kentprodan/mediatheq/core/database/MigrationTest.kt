package io.github.kentprodan.mediatheq.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Robolectric only for the helper's Instrumentation (it reads the exported schemas from the test assets).
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val file = File.createTempFile("migration", ".db").also { it.delete() }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            file,
            BundledSQLiteDriver(),
            MediatheqDatabase::class,
        )

    @Test
    fun `1 to 2 keeps the library and adds the saved queue`() =
        runTest {
            helper.createDatabase(1).apply {
                execSQL(
                    "INSERT INTO track (server_id, id, title, sort_name, duration_ms, is_favorite, play_count, " +
                        "played) VALUES ('s', 't1', 'Moșoi', 'Mosoi', 1000, 0, 0, 0)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(2)

            connection.prepare("SELECT title FROM track").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getText(0)).isEqualTo("Moșoi")
            }
            connection.prepare("SELECT COUNT(*) FROM queue_entry").use {
                it.step()
                assertThat(it.getLong(0)).isEqualTo(0)
            }
            connection.close()
        }
}
