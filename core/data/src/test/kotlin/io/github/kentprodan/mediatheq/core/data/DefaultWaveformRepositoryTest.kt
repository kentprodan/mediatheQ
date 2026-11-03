package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class DefaultWaveformRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository = DefaultWaveformRepository(database.waveformDao())
    private val id = MediaId(ServerId("s"), "t1")

    @After
    fun close() = database.close()

    @Test
    fun `levels keep their shape at one byte each`() =
        runTest {
            repository.save(id, 100, listOf(0f, 0.5f, 1f, 2f, Float.NaN))

            val levels = repository.levels(id).first()!!
            assertThat(levels[0]).isEqualTo(0f)
            assertThat(levels[1]).isWithin(1 / 255f).of(0.5f)
            // Out of range is clamped.
            assertThat(levels.subList(2, 4)).containsExactly(1f, 1f)
            // Not a number (a broken decode) is stored as silence, never a crash.
            assertThat(levels.last()).isEqualTo(0f)
        }

    @Test
    fun `a file of another size is not analyzed yet`() =
        runTest {
            assertThat(repository.isAnalyzed(id, 100)).isFalse()

            repository.save(id, 100, listOf(0.3f))

            assertThat(repository.isAnalyzed(id, 100)).isTrue()
            // The file changed on the server (replaced or re-tagged): analyzed again.
            assertThat(repository.isAnalyzed(id, 101)).isFalse()
        }

    @Test
    fun `a file with no decoder counts as analyzed and shows no waveform`() =
        runTest {
            repository.save(id, 100, null)

            assertThat(repository.isAnalyzed(id, 100)).isTrue()
            assertThat(repository.levels(id).first()).isNull()
        }
}
