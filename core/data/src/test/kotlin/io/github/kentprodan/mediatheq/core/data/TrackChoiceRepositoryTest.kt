package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class TrackChoiceRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository = DefaultTrackChoiceRepository(database.videoDao())
    private val season = MediaId(ServerId("s"), "se1")
    private val episode = MediaId(ServerId("s"), "e1")

    @After
    fun close() = database.close()

    @Test
    fun `a season and an episode keep their own choices, and clearing one leaves the other`() =
        runTest {
            val seasonChoice = TrackChoice(subtitleLanguage = "rum")
            val episodeChoice = TrackChoice(audioIndex = 2, audioLanguage = "eng", subtitleOff = true)

            repository.save(season, seasonChoice)
            repository.save(episode, episodeChoice)
            assertThat(repository.choice(season).first()).isEqualTo(seasonChoice)
            assertThat(repository.choice(episode).first()).isEqualTo(episodeChoice)

            repository.clear(episode)

            assertThat(repository.choice(episode).first()).isNull()
            assertThat(repository.choice(season).first()).isEqualTo(seasonChoice)
        }
}
