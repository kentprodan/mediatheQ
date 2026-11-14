package io.github.kentprodan.mediatheq.core.data

import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.getOrNull
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore
import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.SavedAccount
import io.github.kentprodan.mediatheq.core.datastore.SettingsBackupStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.testing.FakeAccountDatabases
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/** Backups through `ContentResolver` streams, as the system file picker hands them out (content URIs). */
@RunWith(RobolectricTestRunner::class)
class DefaultBackupRepositoryTest {
    private val files = mutableMapOf<String, ByteArrayOutputStream>()
    private val library = InMemoryPreferencesDataStore()
    private val audio = InMemoryPreferencesDataStore()
    private val accountData = InMemoryPreferencesDataStore()
    private val databases = FakeAccountDatabases("mediatheq.db")
    private val accounts = AccountStore(accountData, FakeTokenCipher(), databases.open)
    private val dispatcher = StandardTestDispatcher()
    private val repository =
        DefaultBackupRepository(
            RuntimeEnvironment.getApplication(),
            SettingsBackupStore(
                library,
                audio,
                LibraryPreferencesStore(library),
                AudioPreferencesStore(audio),
                AudioOutputStore(audio),
            ),
            accounts,
            databases,
            ClientInfo("mediatheQ", "Pixel 9", "1.2.3"),
            TestClock(),
            dispatcher,
        )
    private val home =
        StoredAccount("http://nas:8096", ServerId("s1"), "Home", "u1", "Ana", "secret-token-1", "mediatheq.db")
    private val office = SavedAccount("x", "https://office.org", ServerId("s2"), "Office", "u9", "Bo")

    /** A document the picker would hand out; written ones are kept in [files]. */
    private fun uri(name: String): String {
        val uri = Uri.parse("content://documents/$name")
        val resolver = shadowOf(RuntimeEnvironment.getApplication().contentResolver)
        resolver.registerOutputStreamSupplier(uri) { ByteArrayOutputStream().also { files[name] = it } }
        val written = files[name]
        // Not written yet: like a document that is gone.
        resolver.registerInputStreamSupplier(uri) {
            written?.let { ByteArrayInputStream(it.toByteArray()) } ?: throw FileNotFoundException(name)
        }
        return uri.toString()
    }

    private fun file(
        name: String,
        text: String,
    ) {
        files[name] = ByteArrayOutputStream().apply { write(text.encodeToByteArray()) }
    }

    private fun text(name: String) = files.getValue(name).toByteArray().decodeToString()

    @Test
    fun `a backup holds the settings and addresses, and never a token or the DeviceId`() =
        runTest(dispatcher) {
            accounts.save(home)
            val deviceId = accounts.deviceId()
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }

            assertThat(repository.write(uri("b.json"))).isTrue()

            val text = text("b.json")
            assertThat(text).contains("http://nas:8096")
            assertThat(text).doesNotContain("secret-token-1")
            assertThat(text).doesNotContain(deviceId)
            val check = repository.read(uri("b.json")) as BackupCheck.Valid
            assertThat(check.summary.settingsCount).isEqualTo(1)
            assertThat(check.summary.appVersion).isEqualTo("1.2.3")
            assertThat(check.summary.accounts).containsExactly(BackupAccountSummary("Home", "Ana", "http://nas:8096"))
        }

    @Test
    fun `a restore replaces the settings and adds the missing accounts signed out, once`() =
        runTest(dispatcher) {
            accounts.save(home)
            accounts.saveSignedOut(office.copy(id = "mediatheq-old.db"))
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }
            repository.write(uri("b.json"))
            accountData.edit { it.clear() }
            accounts.save(home)
            library.edit {
                it[stringPreferencesKey("theme_mode")] = "Light"
                it[intPreferencesKey("lp_columns")] = 4
            }

            val result = repository.restore(uri("b.json"))
            repository.restore(uri("b.json"))

            assertThat(result).isEqualTo(BackupRestore.Restored(toSignIn = 1))
            assertThat(library.current.asMap().mapKeys { it.key.name }).containsExactly("theme_mode", "Dark")
            val saved = accounts.saved.first()
            assertThat(saved.map { it.serverName to it.signedIn }).containsExactly("Home" to true, "Office" to false)
            assertThat(saved.last().id).isNotEqualTo(databases.open)
            assertThat(
                accounts.account
                    .first()
                    .getOrNull()
                    ?.accessToken,
            ).isEqualTo("secret-token-1")
        }

    @Test
    fun `the Video settings come back with a restore`() =
        runTest(dispatcher) {
            val video = DefaultVideoPreferencesRepository(AudioPreferencesStore(audio))
            val chosen =
                VideoPreferences(
                    meteredQuality = io.github.kentprodan.mediatheq.core.model.VideoQuality.P720,
                    audioLanguage = "en",
                    autoPlayNext = false,
                )
            video.update { chosen }
            repository.write(uri("b.json"))
            video.update { VideoPreferences() }

            repository.restore(uri("b.json"))

            assertThat(video.preferences.first()).isEqualTo(chosen)
        }

    @Test
    fun `a broken file changes nothing`() =
        runTest(dispatcher) {
            file("broken.json", """{"format": 1, "created": "nope"}""")
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }

            assertThat(repository.read(uri("broken.json"))).isEqualTo(BackupCheck.Invalid)
            assertThat(repository.restore(uri("broken.json"))).isEqualTo(BackupRestore.Failed(BackupCheck.Invalid))
            assertThat(library.current[stringPreferencesKey("theme_mode")]).isEqualTo("Dark")
        }

    @Test
    fun `a setting of a type this version reads differently restores nothing`() =
        runTest(dispatcher) {
            file(
                "typed.json",
                """{"format": 1, "created": "2026-01-01T00:00:00Z",
                    "settings": {"library": {"theme_mode": {"type": "int", "value": 3}}},
                    "accounts": [{"provider": "jellyfin", "serverId": "s2", "address": "https://office.org", "userId": "u9"}]}""",
            )
            library.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }

            assertThat(repository.restore(uri("typed.json"))).isEqualTo(BackupRestore.Failed(BackupCheck.Invalid))
            assertThat(library.current[stringPreferencesKey("theme_mode")]).isEqualTo("Dark")
            assertThat(accounts.saved.first()).isEmpty()
        }

    @Test
    fun `a file that is not there cannot be read, and one far too big is no backup`() =
        runTest(dispatcher) {
            file("big.json", "x".repeat(1_000_001))

            assertThat(repository.read(uri("missing.json"))).isEqualTo(BackupCheck.Unreadable)
            assertThat(repository.read(uri("big.json"))).isEqualTo(BackupCheck.Invalid)
        }

    @Test
    fun `settings that cannot be written leave everything as it was`() =
        runTest(dispatcher) {
            repository.write(uri("b.json"))
            library.failingWrites = true

            assertThat(repository.restore(uri("b.json"))).isEqualTo(BackupRestore.NotSaved)
        }
}
