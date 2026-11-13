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

    @Test
    fun `4 to 5 keeps pending user data and adds download origins`() =
        runTest {
            helper.createDatabase(4).apply {
                execSQL(
                    "INSERT INTO pending_user_data (server_id, user_id, item_id, is_favorite, sets_rating, rating, " +
                        "before_favorite, before_rating, seq) VALUES ('s', 'u', 't1', 1, 0, NULL, 0, NULL, 1)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(5)

            connection.prepare("SELECT item_id FROM pending_user_data").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getText(0)).isEqualTo("t1")
            }
            connection.execSQL(
                "INSERT INTO download_origin (server_id, track_id, kind, collection_id) VALUES ('s', 't1', 'Album', 'a1')",
            )
            connection.prepare("SELECT kind, collection_id FROM download_origin").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getText(0)).isEqualTo("Album")
                assertThat(it.getText(1)).isEqualTo("a1")
            }
            connection.close()
        }

    @Test
    fun `5 to 6 keeps download origins and adds an empty waveform table`() =
        runTest {
            helper.createDatabase(5).apply {
                execSQL(
                    "INSERT INTO download_origin (server_id, track_id, kind, collection_id) VALUES ('s', 't1', 'Album', 'a1')",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(6)

            connection.prepare("SELECT COUNT(*) FROM download_origin").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(1)
            }
            connection.prepare("SELECT COUNT(*) FROM waveform").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(0)
            }
            connection.close()
        }

    @Test
    fun `6 to 7 adds empty genre links and makes the next start sync them`() =
        runTest {
            helper.createDatabase(6).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_server_id, checkpoint_user_id, checkpoint_started_at, checkpoint_step, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 's', 'u', 1760000000000, 'Tracks', 500)",
                )
                execSQL(
                    "INSERT INTO track_staging (server_id, id, title, sort_name, duration_ms, is_favorite, " +
                        "play_count, played) VALUES ('s', 't1', 'One', 'One', 1000, 0, 0, 0)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(7)

            connection.prepare("SELECT last_full_sync_at, owner_server_id, checkpoint_step FROM sync_state").use {
                assertThat(it.step()).isTrue()
                // Dated at the epoch: still synced once, and over a day old, so the next start syncs.
                assertThat(it.getLong(0)).isEqualTo(0)
                assertThat(it.getText(1)).isEqualTo("s")
                assertThat(it.isNull(2)).isTrue()
            }
            connection.prepare("SELECT COUNT(*) FROM track_staging").use {
                it.step()
                assertThat(it.getLong(0)).isEqualTo(0)
            }
            connection.prepare("SELECT COUNT(*) FROM album_genre").use {
                it.step()
                assertThat(it.getLong(0)).isEqualTo(0)
            }
            connection.close()
        }

    @Test
    fun `7 to 8 adds empty label links, drops staged genre links and makes the next start sync them`() =
        runTest {
            helper.createDatabase(7).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_server_id, checkpoint_user_id, checkpoint_started_at, checkpoint_step, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 's', 'u', 1760000000000, 'Tracks', 500)",
                )
                execSQL(
                    "INSERT INTO album_genre (server_id, album_id, genre_id, genre_name) VALUES ('s', 'a', 'g', 'Rock')",
                )
                execSQL(
                    "INSERT INTO track_genre_staging (server_id, track_id, genre_id, genre_name) " +
                        "VALUES ('s', 't', 'g', 'Rock')",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(8)

            connection.prepare("SELECT last_full_sync_at, checkpoint_step FROM sync_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(0)
                assertThat(it.isNull(1)).isTrue()
            }

            fun count(table: String) =
                connection.prepare("SELECT COUNT(*) FROM $table").use {
                    it.step()
                    it.getLong(0)
                }
            assertThat(count("track_genre_staging")).isEqualTo(0)
            assertThat(count("album_genre")).isEqualTo(1)
            assertThat(count("album_label")).isEqualTo(0)
            connection.close()
        }

    @Test
    fun `8 to 9 adds the album page columns and an empty resume table, and makes the next start sync`() =
        runTest {
            helper.createDatabase(8).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_server_id, checkpoint_user_id, checkpoint_started_at, checkpoint_step, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 's', 'u', 1760000000000, 'Albums', 0)",
                )
                execSQL(
                    "INSERT INTO album_label_staging (server_id, album_id, label_id, label_name) " +
                        "VALUES ('s', 'a', 'l', 'Atlantic')",
                )
                execSQL(
                    "INSERT INTO album (server_id, id, title, sort_name, track_count, duration_ms, is_favorite, " +
                        "play_count, played) VALUES ('s', 'a', 'A', 'A', 0, 0, 0, 0, 0)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(9)

            connection.prepare("SELECT last_full_sync_at, checkpoint_step FROM sync_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(0)
                assertThat(it.isNull(1)).isTrue()
            }
            connection.prepare("SELECT overview, release_date FROM album").use {
                assertThat(it.step()).isTrue()
                assertThat(it.isNull(0)).isTrue()
                assertThat(it.isNull(1)).isTrue()
            }

            fun count(table: String) =
                connection.prepare("SELECT COUNT(*) FROM $table").use {
                    it.step()
                    it.getLong(0)
                }
            assertThat(count("album_label_staging")).isEqualTo(0)
            assertThat(count("album_resume")).isEqualTo(0)
            connection.close()
        }

    @Test
    fun `9 to 10 adds the artist backdrop and biography and makes the next start sync`() =
        runTest {
            helper.createDatabase(9).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_server_id, checkpoint_user_id, checkpoint_started_at, checkpoint_step, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 's', 'u', 1760000000000, 'Albums', 0)",
                )
                execSQL(
                    "INSERT INTO artist (server_id, id, name, sort_name, is_album_artist, is_favorite, play_count, " +
                        "played) VALUES ('s', 'ar', 'A', 'A', 1, 0, 0, 0)",
                )
                execSQL(
                    "INSERT INTO album_resume (server_id, album_id, track_id, position_ms) VALUES ('s', 'a', 't', 5)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(10)

            connection.prepare("SELECT last_full_sync_at, checkpoint_step FROM sync_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(0)
                assertThat(it.isNull(1)).isTrue()
            }
            connection.prepare("SELECT backdrop_image_tag, overview FROM artist").use {
                assertThat(it.step()).isTrue()
                assertThat(it.isNull(0)).isTrue()
                assertThat(it.isNull(1)).isTrue()
            }
            // Where albums were left is the app's own data: a resync keeps it.
            connection.prepare("SELECT COUNT(*) FROM album_resume").use {
                it.step()
                assertThat(it.getLong(0)).isEqualTo(1)
            }
            connection.close()
        }

    @Test
    fun `10 to 11 adds where playlists were left and keeps the queue and the sync`() =
        runTest {
            helper.createDatabase(10).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 0)",
                )
                execSQL(
                    "INSERT INTO queue_entry (position, entry_id, server_id, track_id, shuffle_position) " +
                        "VALUES (0, 'e0', 's', 't', 0)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(11)

            connection.prepare("SELECT last_full_sync_at FROM sync_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(1760000000000)
            }
            connection.prepare("SELECT entry_id, playlist_id FROM queue_entry").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getText(0)).isEqualTo("e0")
                assertThat(it.isNull(1)).isTrue()
            }
            connection.prepare("SELECT COUNT(*) FROM playlist_resume").use {
                it.step()
                assertThat(it.getLong(0)).isEqualTo(0)
            }
            connection.close()
        }

    @Test
    fun `11 to 12 adds empty video tables, keeps the music and makes the next start sync`() =
        runTest {
            helper.createDatabase(11).apply {
                execSQL(
                    "INSERT INTO sync_state (id, last_full_sync_at, owner_server_id, owner_user_id, " +
                        "checkpoint_server_id, checkpoint_user_id, checkpoint_started_at, checkpoint_step, " +
                        "checkpoint_index) VALUES (0, 1760000000000, 's', 'u', 's', 'u', 1760000000000, 'Tracks', 500)",
                )
                execSQL(
                    "INSERT INTO track (server_id, id, title, sort_name, duration_ms, is_favorite, play_count, " +
                        "played) VALUES ('s', 't1', 'Moșoi', 'Mosoi', 1000, 0, 0, 0)",
                )
                execSQL(
                    "INSERT INTO track_staging (server_id, id, title, sort_name, duration_ms, is_favorite, " +
                        "play_count, played) VALUES ('s', 't2', 'T', 'T', 1000, 0, 0, 0)",
                )
                close()
            }

            val connection = helper.runMigrationsAndValidate(12)

            connection.prepare("SELECT last_full_sync_at, checkpoint_step FROM sync_state").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(0)
                assertThat(it.isNull(1)).isTrue()
            }
            connection.prepare("SELECT title FROM track").use {
                assertThat(it.step()).isTrue()
                assertThat(it.getText(0)).isEqualTo("Moșoi")
            }
            for (table in listOf("track_staging", "video", "series", "season", "video_stream", "video_staging")) {
                connection.prepare("SELECT COUNT(*) FROM $table").use {
                    it.step()
                    assertThat(it.getLong(0)).isEqualTo(0)
                }
            }
            connection.close()
        }
}
