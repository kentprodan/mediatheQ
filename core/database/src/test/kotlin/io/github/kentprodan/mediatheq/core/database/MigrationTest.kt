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

    @Test
    fun `2 to 3 keeps the saved queue state with repeat off`() =
        runTest {
            helper.createDatabase(2).apply {
                execSQL("INSERT INTO queue_state (id, current_position, position_ms, shuffle) VALUES (0, 2, 5000, 1)")
                close()
            }

            val connection = helper.runMigrationsAndValidate(3)

            connection.prepare("SELECT current_position, shuffle, repeat_mode FROM queue_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(2)
                assertThat(it.getLong(1)).isEqualTo(1)
                assertThat(it.getLong(2)).isEqualTo(0)
            }
            connection.close()
        }

    @Test
    fun `3 to 4 keeps the saved queue and adds pending user data`() =
        runTest {
            helper.createDatabase(3).apply {
                execSQL("INSERT INTO queue_state (id, current_position, position_ms, shuffle) VALUES (0, 2, 5000, 1)")
                close()
            }

            val connection = helper.runMigrationsAndValidate(4)

            connection.prepare("SELECT position_ms FROM queue_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(5000)
            }
            connection.execSQL(
                "INSERT INTO pending_user_data (server_id, user_id, item_id, is_favorite, sets_rating, rating, " +
                    "before_favorite, before_rating, seq) VALUES ('s', 'u', 't1', 1, 1, 8.0, 0, NULL, 1)",
            )
            connection.prepare("SELECT is_favorite, rating FROM pending_user_data").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(1)
                assertThat(it.getDouble(1)).isEqualTo(8.0)
            }
            connection.close()
        }
}
