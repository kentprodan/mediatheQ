package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SettingsBackupStoreTest {
    private val library = InMemoryPreferencesDataStore()
    private val audio = InMemoryPreferencesDataStore()
    private val store =
        SettingsBackupStore(
            library,
            audio,
            LibraryPreferencesStore(library),
            AudioPreferencesStore(audio),
            AudioOutputStore(audio),
        )

    @Test
    fun `a snapshot holds every setting of both files with its type, never history or state`() =
        runTest {
            library.edit {
                it[stringPreferencesKey("theme_mode")] = "Dark"
                it[intPreferencesKey("album_columns")] = 3
                it[booleanPreferencesKey("dynamic_color")] = false
                it[stringPreferencesKey("recent_searches")] = "moșoi"
            }
            audio.edit {
                it[longPreferencesKey("streaming_cache_gb")] = 4L
                it[floatPreferencesKey("out.phone.gain")] = 1.5f
                it[stringSetPreferencesKey("outputs")] = setOf("usb-1")
                it[stringPreferencesKey("current_output")] = "usb-1"
            }

            val snapshot = store.snapshot()

            assertThat(
                snapshot.library,
            ).containsExactly("theme_mode", "Dark", "album_columns", 3, "dynamic_color", false)
            assertThat(snapshot.audio)
                .containsExactly("streaming_cache_gb", 4L, "out.phone.gain", 1.5f, "outputs", setOf("usb-1"))
        }

    @Test
    fun `a replace makes the settings exactly the snapshot and keeps history`() =
        runTest {
            library.edit {
                it[stringPreferencesKey("theme_mode")] = "Dark"
                it[intPreferencesKey("lp_columns")] = 4
                it[stringPreferencesKey("recent_searches")] = "moșoi"
            }

            store.replace(SettingsSnapshot(mapOf("theme_mode" to "Light", "nav_transparency" to 50), emptyMap()))

            val values = library.current.asMap().mapKeys { it.key.name }
            assertThat(
                values,
            ).containsExactly("theme_mode", "Light", "nav_transparency", 50, "recent_searches", "moșoi")
        }

    @Test
    fun `the output in use stays only while it has a restored profile`() =
        runTest {
            audio.edit { it[stringPreferencesKey("current_output")] = "usb-1" }

            store.replace(SettingsSnapshot(emptyMap(), mapOf("outputs" to setOf("bt-2"))))
            assertThat(audio.current[stringPreferencesKey("current_output")]).isNull()

            audio.edit { it[stringPreferencesKey("current_output")] = "bt-2" }
            store.replace(SettingsSnapshot(emptyMap(), mapOf("outputs" to setOf("bt-2"))))
            assertThat(audio.current[stringPreferencesKey("current_output")]).isEqualTo("bt-2")
        }

    @Test
    fun `a value of a type this version reads differently writes nothing`() =
        runTest {
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }
            audio.edit { it[stringSetPreferencesKey("outputs")] = setOf("bt-2") }

            for (snapshot in listOf(
                SettingsSnapshot(mapOf("theme_mode" to 3), emptyMap()),
                SettingsSnapshot(mapOf("theme_mode" to "Light"), mapOf("outputs" to "bt-2")),
                SettingsSnapshot(
                    mapOf("theme_mode" to "Light"),
                    mapOf(
                        "outputs" to setOf("bt-2"),
                        "out.bt-2.bypass" to "yes",
                    ),
                ),
            )) {
                assertThrows(WrongTypeException::class.java) {
                    kotlinx.coroutines.runBlocking { store.replace(snapshot) }
                }
            }

            assertThat(library.current[stringPreferencesKey("theme_mode")]).isEqualTo("Dark")
            assertThat(audio.current[stringSetPreferencesKey("outputs")]).containsExactly("bt-2")
        }

    @Test
    fun `a key this version does not know is restored as it is`() =
        runTest {
            store.replace(SettingsSnapshot(mapOf("from_a_newer_version" to 7), emptyMap()))

            assertThat(library.current[intPreferencesKey("from_a_newer_version")]).isEqualTo(7)
        }

    @Test
    fun `when the second file cannot be written the first is written back`() =
        runTest {
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }
            audio.failingWrites = true

            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking {
                    store.replace(
                        SettingsSnapshot(mapOf("theme_mode" to "Light"), emptyMap()),
                    )
                }
            }

            assertThat(library.current[stringPreferencesKey("theme_mode")]).isEqualTo("Dark")
        }
}
