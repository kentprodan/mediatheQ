package io.github.kentprodan.mediatheq.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.BackupAccountSummary
import io.github.kentprodan.mediatheq.core.data.BackupCheck
import io.github.kentprodan.mediatheq.core.data.BackupRestore
import io.github.kentprodan.mediatheq.core.data.BackupSummary
import io.github.kentprodan.mediatheq.core.testing.FakeBackupRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class BackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val backups = FakeBackupRepository()
    private val viewModel = BackupViewModel(backups)
    private val summary =
        BackupSummary(
            Instant.parse("2026-11-12T12:00:00Z"),
            "1.2.3",
            42,
            listOf(BackupAccountSummary("Home", "Ana", "http://nas")),
        )

    private val state get() = viewModel.uiState.value

    @Test
    fun `starts idle`() {
        assertThat(state).isEqualTo(BackupUiState())
    }

    @Test
    fun `a backup written says so, one that could not be written too`() {
        viewModel.onAction(BackupAction.Write("content://b"))
        assertThat(state.message).isEqualTo(BackupMessage.Saved)
        assertThat(backups.written).containsExactly("content://b")

        backups.writes = false
        viewModel.onAction(BackupAction.Write("content://c"))
        assertThat(state.message).isEqualTo(BackupMessage.NotWritten)
        assertThat(state.busy).isFalse()
    }

    @Test
    fun `a picked backup asks first with its summary, then restores`() {
        backups.check = BackupCheck.Valid(summary)
        backups.restoreResult = BackupRestore.Restored(toSignIn = 2)

        viewModel.onAction(BackupAction.Pick("content://b"))
        assertThat(state.pending).isEqualTo(PendingRestore("content://b", summary))
        assertThat(backups.restored).isEmpty()

        viewModel.onAction(BackupAction.ConfirmRestore)

        assertThat(state.pending).isNull()
        assertThat(state.message).isEqualTo(BackupMessage.Restored(2))
        assertThat(backups.restored).containsExactly("content://b")
    }

    @Test
    fun `dismissing the summary restores nothing`() {
        backups.check = BackupCheck.Valid(summary)
        viewModel.onAction(BackupAction.Pick("content://b"))

        viewModel.onAction(BackupAction.DismissRestore)

        assertThat(state.pending).isNull()
        assertThat(backups.restored).isEmpty()
    }

    @Test
    fun `a file that is no backup or cannot be read says so and restores nothing`() {
        backups.check = BackupCheck.Invalid
        viewModel.onAction(BackupAction.Pick("content://x"))
        assertThat(state.message).isEqualTo(BackupMessage.Invalid)

        backups.check = BackupCheck.Unreadable
        viewModel.onAction(BackupAction.Pick("content://y"))
        assertThat(state.message).isEqualTo(BackupMessage.Unreadable)
        assertThat(state.pending).isNull()
    }

    @Test
    fun `settings that could not be saved say nothing changed`() {
        backups.check = BackupCheck.Valid(summary)
        backups.restoreResult = BackupRestore.NotSaved
        viewModel.onAction(BackupAction.Pick("content://b"))

        viewModel.onAction(BackupAction.ConfirmRestore)

        assertThat(state.message).isEqualTo(BackupMessage.NotRestored)
    }

    @Test
    fun `a message goes when dismissed`() {
        viewModel.onAction(BackupAction.Write("content://b"))

        viewModel.onAction(BackupAction.DismissMessage)

        assertThat(state.message).isNull()
    }
}
