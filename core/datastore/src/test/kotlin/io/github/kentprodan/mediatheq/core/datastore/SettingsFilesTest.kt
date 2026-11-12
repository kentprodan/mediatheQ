package io.github.kentprodan.mediatheq.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The backup (#150) holds the Library and Audio preference files whole. A preference stored anywhere else would be
 * missing from it: this test fails when the app gains a preference file, so it is either backed up or knowingly left out.
 */
class SettingsFilesTest {
    private val root =
        generateSequence(File("").absoluteFile) {
            it.parentFile
        }.first { File(it, "settings.gradle.kts").exists() }

    private val opened =
        root
            .walkTopDown()
            .filter {
                it.isFile && it.extension == "kt" &&
                    "${File.separator}src${File.separator}main${File.separator}" in it.path
            }.flatMap { file -> CALL.findAll(file.readText()).map { it.groupValues[2].trim() } }
            .toSet()

    @Test
    fun `every preference file is either in the backup or left out on purpose`() {
        assertThat(opened)
            .containsExactly(
                // In the backup: every setting.
                "\"library_prefs\"",
                "\"audio_prefs\"",
                // Left out: tokens and the DeviceId; which database is open; whether a queue was saved; the SD card.
                "\"account\"",
                "\"accounts\"",
                "\"saved_queue\"",
                "DownloadStorage.PREFERENCES",
            )
    }

    @Test
    fun `only history and state are left out of the backed-up files`() {
        assertThat(SettingsBackupStore.EXCLUDED).containsExactly("recent_searches", "current_output")
    }

    private companion object {
        val CALL = Regex("""(preferencesDataStoreFile|getSharedPreferences)\(\s*([^,)]+)""")
    }
}
