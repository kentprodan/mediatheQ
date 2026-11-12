package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.SettingsSnapshot
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test
import kotlin.time.Instant

class BackupFileTest {
    private val backup =
        BackupFile(
            format = BACKUP_FORMAT,
            appVersion = "1.2.3",
            created = Instant.parse("2026-11-12T10:00:00Z"),
            settings =
                SettingsSnapshot(
                    library =
                        mapOf(
                            "theme_mode" to "Dark",
                            "album_columns" to 3,
                            "dynamic_color" to false,
                            "home_hidden" to setOf("Recent", "Mixes"),
                        ),
                    audio = mapOf("streaming_cache_gb" to 4L, "out.phone.gain" to 1.5f, "tr_ratio" to 0.25),
                ),
            accounts = listOf(BackupAccount("jellyfin", ServerId("s1"), "Home", "http://nas:8096", "u1", "Ana")),
        )

    @Test
    fun `every value type of both groups and the accounts come back as they were`() {
        assertThat(decodeBackup(encodeBackup(backup))).isEqualTo(backup)
    }

    @Test
    fun `the file is readable JSON with no secret field`() {
        val text = encodeBackup(backup)

        assertThat(text).contains("\"theme_mode\"")
        listOf("token", "password", "device_id", "access_token_encrypted").forEach {
            assertThat(text.lowercase()).doesNotContain(it)
        }
    }

    @Test
    fun `a newer file is read as far as it is understood`() {
        val text =
            """
            {"format": 7, "app": "9.0", "created": "2027-01-01T00:00:00Z", "newSection": {"x": 1},
             "settings": {"library": {"theme_mode": {"type": "string", "value": "Light"},
                                      "hologram": {"type": "vector3", "value": [1, 2, 3]},
                                      "lp_columns": {"type": "int", "value": "not a number"}},
                          "video": {"subtitles": {"type": "boolean", "value": true}}},
             "accounts": [{"provider": "plex", "serverId": "p", "address": "https://plex", "userId": "u"}]}
            """.trimIndent()

        val decoded = decodeBackup(text)

        assertThat(decoded?.format).isEqualTo(7)
        assertThat(decoded?.settings).isEqualTo(SettingsSnapshot(mapOf("theme_mode" to "Light"), emptyMap()))
        assertThat(decoded?.accounts?.single()?.provider).isEqualTo("plex")
    }

    @Test
    fun `anything that is not a backup is refused`() {
        listOf(
            "",
            "not json",
            "[]",
            """{"app": "1.0", "created": "2026-01-01T00:00:00Z"}""",
            """{"format": 0, "created": "2026-01-01T00:00:00Z"}""",
            """{"format": 1, "created": "yesterday"}""",
        ).forEach { assertThat(decodeBackup(it)).isNull() }
    }

    @Test
    fun `an account without an address or ids is left out`() {
        val text =
            """{"format": 1, "created": "2026-01-01T00:00:00Z",
                "accounts": [{"provider": "jellyfin", "serverId": "", "address": "http://x", "userId": "u"}]}"""

        assertThat(decodeBackup(text)?.accounts).isEmpty()
    }
}
