package io.github.kentprodan.mediatheq.feature.nowplaying

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.CoverTap
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLyricsRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.FakeWaveformRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.ui.QualityKind
import io.github.kentprodan.mediatheq.core.ui.QualityLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NowPlayingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackConnection()
    private val library = FakeLibraryRepository()
    private val userData = FakeUserDataRepository()
    private val lyrics = FakeLyricsRepository()
    private val libraryPreferences = FakeLibraryPreferencesRepository()
    private val waveforms = FakeWaveformRepository()
    private val downloads = FakeDownloadRepository()
    private val artwork = Artwork("al1", "tag")
    private val playing =
        PlayerUiModel(
            item =
                NowPlayingItem(
                    MediaId(ServerId("s"), "t1"),
                    "Strobe",
                    "deadmau5",
                    artwork,
                ),
            isPlaying = true,
            duration = 10.minutes,
            isSeekable = true,
            hasNext = true,
            hasPrevious = true,
        )

    @Test
    fun `nothing queued hides the player`() =
        runTest {
            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)
            }
        }

    @Test
    fun `the queue entry shows with its cover and controls, and follows pause`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)

                playback.state.value = playing
                assertThat(awaitItem())
                    .isEqualTo(
                        NowPlayingUiState.Playing(
                            title = "Strobe",
                            artist = "deadmau5",
                            artwork = artwork,
                            isPlaying = true,
                            duration = 10.minutes,
                            isSeekable = true,
                            hasNext = true,
                            hasPrevious = true,
                            trackId = MediaId(ServerId("s"), "t1"),
                        ),
                    )

                playback.state.value = playing.copy(isPlaying = false)
                assertThat((awaitItem() as NowPlayingUiState.Playing).isPlaying).isFalse()

                playback.state.value = PlayerUiModel()
                assertThat(awaitItem()).isEqualTo(NowPlayingUiState.Hidden)
            }
        }

    @Test
    fun `transport actions reach the player`() {
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.TogglePlayPause)
        viewModel.onAction(NowPlayingAction.Next)
        viewModel.onAction(NowPlayingAction.Previous)

        assertThat(playback.commands).containsExactly("togglePlayPause", "next", "previous").inOrder()
    }

    @Test
    fun `seeking turns the fraction into a position in the current track`() {
        playback.state.value = playing
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.SeekTo(0.25f))
        viewModel.onAction(NowPlayingAction.SeekTo(1.5f))

        assertThat(playback.commands).containsExactly("seekTo ${150.seconds}", "seekTo ${10.minutes}").inOrder()
    }

    @Test
    fun `the playing entry, shuffle and repeat reach the screens`() =
        runTest {
            playback.state.value = playing.copy(currentEntryId = "e2", shuffle = true, repeatMode = RepeatMode.One)

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.currentEntryId).isEqualTo("e2")
                assertThat(state.shuffle).isTrue()
                assertThat(state.repeatMode).isEqualTo(RepeatMode.One)
            }
        }

    @Test
    fun `shuffle and repeat reach the player`() {
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.SetShuffle(true))
        viewModel.onAction(NowPlayingAction.SetRepeatMode(RepeatMode.One))

        assertThat(playback.commands).containsExactly("shuffle true", "repeat One").inOrder()
    }

    @Test
    fun `repeat cycles off, all, one, off`() {
        assertThat(RepeatMode.entries.map { it.next() })
            .containsExactly(RepeatMode.All, RepeatMode.One, RepeatMode.Off)
            .inOrder()
    }

    @Test
    fun `the queue comes from the player`() {
        val queue = listOf(QueueItem("e1", MediaId(ServerId("s"), "t1"), "Strobe", "deadmau5"))
        playback.queue.value = queue

        assertThat(
            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).queue.value,
        ).isEqualTo(queue)
    }

    @Test
    fun `queue actions reach the player by entry`() {
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.SkipToQueueItem("e1"))
        viewModel.onAction(NowPlayingAction.RemoveQueueItem("e2"))
        viewModel.onAction(NowPlayingAction.MoveQueueItem("e3", 0))

        assertThat(playback.commands).containsExactly("skipTo e1", "remove e2", "move e3 0").inOrder()
    }

    @Test
    fun `seeking without a known duration does nothing`() {
        playback.state.value = playing.copy(duration = Duration.ZERO)

        NowPlayingViewModel(
            playback,
            library,
            userData,
            lyrics,
            libraryPreferences,
            waveforms,
            downloads,
        ).onAction(NowPlayingAction.SeekTo(0.5f))

        assertThat(playback.commands).isEmpty()
    }

    private val track = Track(MediaId(ServerId("s"), "t1"), "Strobe", emptyList(), 10.minutes)

    @Test
    fun `favorite and stars of the playing track come from the library`() =
        runTest {
            library.tracks.value = listOf(track.copy(userData = UserData(isFavorite = true, rating = 8.0)))
            playback.state.value = playing

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.isFavorite).isTrue()
                assertThat(state.halfStars).isEqualTo(8)

                library.tracks.value = listOf(track.copy(userData = UserData(isFavorite = false)))
                val changed = awaitItem() as NowPlayingUiState.Playing
                assertThat(changed.isFavorite).isFalse()
                assertThat(changed.halfStars).isNull()
            }
        }

    @Test
    fun `a track not in the library shows as not favorite and unrated`() =
        runTest {
            playback.state.value = playing

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                val state = awaitItem() as NowPlayingUiState.Playing
                assertThat(state.isFavorite).isFalse()
                assertThat(state.halfStars).isNull()
            }
        }

    @Test
    fun `favorite and rating change the playing track`() =
        runTest {
            playback.state.value = playing
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

            viewModel.onAction(NowPlayingAction.SetFavorite(true))
            viewModel.onAction(NowPlayingAction.Rate(6))
            viewModel.onAction(NowPlayingAction.Rate(null))

            assertThat(userData.calls).containsExactly("favorite:t1:true", "rating:t1:6", "rating:t1:null").inOrder()
        }

    @Test
    fun `with nothing playing, favorite and rating do nothing`() {
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.SetFavorite(true))
        viewModel.onAction(NowPlayingAction.Rate(6))

        assertThat(userData.calls).isEmpty()
    }

    @Test
    fun `an Atmos track playing converted says so on the player`() =
        runTest {
            playback.state.value = playing.copy(output = AudioOutputInfo(atmosConverted = true))

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                assertThat((awaitItem() as NowPlayingUiState.Playing).atmosConverted).isTrue()
            }
        }

    @Test
    fun `bit-perfect output shows its sample rate on the player`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            playback.state.value = playing.copy(output = AudioOutputInfo(bitPerfectSampleRateHz = 96_000))

            viewModel.uiState.test {
                assertThat((awaitItem() as NowPlayingUiState.Playing).bitPerfectSampleRateHz).isEqualTo(96_000)
            }
        }

    private val syncedLyrics = Lyrics(listOf(LyricLine("one", 1.seconds), LyricLine("two", 4.seconds)))

    private suspend fun ReceiveTurbine<LyricsUiState>.awaitLoaded(): LyricsUiState {
        var item = awaitItem()
        while (item == LyricsUiState.Loading) item = awaitItem()
        return item
    }

    @Test
    fun `lyrics show loading until the server answers`() =
        runTest {
            playback.state.value = playing
            lyrics.gate = CompletableDeferred()
            lyrics.lyrics[track.id] = syncedLyrics

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).lyrics.test {
                assertThat(awaitItem()).isEqualTo(LyricsUiState.Loading)
                lyrics.gate!!.complete(Unit)
                assertThat(awaitItem()).isEqualTo(LyricsUiState.Content(syncedLyrics))
            }
        }

    @Test
    fun `plain lyrics show as content`() =
        runTest {
            playback.state.value = playing
            val plain = Lyrics(listOf(LyricLine("one")))
            lyrics.lyrics[track.id] = plain

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).lyrics.test {
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.Content(plain))
            }
        }

    @Test
    fun `a track without lyrics shows none`() =
        runTest {
            playback.state.value = playing

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).lyrics.test {
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.None)
            }
        }

    @Test
    fun `nothing playing shows no lyrics and asks nothing`() =
        runTest {
            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).lyrics.test {
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.None)
            }
            assertThat(lyrics.calls).isEmpty()
        }

    @Test
    fun `a failure shows the error and retry asks again`() =
        runTest {
            playback.state.value = playing
            lyrics.failure = MqError.Network
            lyrics.lyrics[track.id] = syncedLyrics
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

            viewModel.lyrics.test {
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.Error(MqError.Network))

                lyrics.failure = null
                viewModel.onAction(NowPlayingAction.RetryLyrics)
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.Content(syncedLyrics))
            }
            assertThat(lyrics.calls).hasSize(2)
        }

    @Test
    fun `the next track's lyrics replace the previous ones, asked once per track`() =
        runTest {
            playback.state.value = playing
            val next = MediaId(ServerId("s"), "t2")
            val nextLyrics = Lyrics(listOf(LyricLine("next")))
            lyrics.lyrics[track.id] = syncedLyrics
            lyrics.lyrics[next] = nextLyrics

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).lyrics.test {
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.Content(syncedLyrics))
                // Pausing is not a new track.
                playback.state.value = playing.copy(isPlaying = false)
                playback.state.value = playing.copy(item = playing.item!!.copy(id = next))
                assertThat(awaitLoaded()).isEqualTo(LyricsUiState.Content(nextLyrics))
            }
            assertThat(lyrics.calls).containsExactly(track.id, next).inOrder()
        }

    @Test
    fun `a tapped line seeks to its start, within the track`() {
        playback.state.value = playing
        val viewModel =
            NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

        viewModel.onAction(NowPlayingAction.SeekToTime(42.seconds))
        viewModel.onAction(NowPlayingAction.SeekToTime(20.minutes))

        assertThat(playback.commands).containsExactly("seekTo ${42.seconds}", "seekTo ${10.minutes}").inOrder()
    }

    @Test
    fun `R12 a tapped line does not seek a stream that cannot be seeked`() {
        playback.state.value = playing.copy(isSeekable = false)

        NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            .onAction(NowPlayingAction.SeekToTime(42.seconds))

        assertThat(playback.commands).isEmpty()
    }

    @Test
    fun `the rating mode reaches the player`() =
        runTest {
            playback.state.value = playing
            libraryPreferences.setRatingMode(RatingMode.HalfStars)

            NowPlayingViewModel(
                playback,
                library,
                userData,
                lyrics,
                libraryPreferences,
                waveforms,
                downloads,
            ).uiState.test {
                assertThat((awaitItem() as NowPlayingUiState.Playing).ratingMode).isEqualTo(RatingMode.HalfStars)
            }
        }

    @Test
    fun `starting playback opens the player when the setting is on`() =
        runTest {
            libraryPreferences.setNavigation(NavigationPreferences(expandPlayer = true))
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.autoExpand.test {
                assertThat(awaitItem()).isFalse()

                playback.play(listOf(track()))
                playback.state.value = playing
                assertThat(awaitItem()).isTrue()

                viewModel.onAction(NowPlayingAction.AutoExpanded)
                assertThat(awaitItem()).isFalse()
                assertThat(playback.expandRequested.value).isFalse()
            }
        }

    @Test
    fun `with the setting off starting playback never opens the player, now or later`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.autoExpand.test {
                assertThat(awaitItem()).isFalse()

                playback.play(listOf(track()))
                playback.state.value = playing
                libraryPreferences.setNavigation(NavigationPreferences(expandPlayer = true))

                expectNoEvents()
                assertThat(playback.expandRequested.value).isFalse()
            }
        }

    @Test
    fun `queueing next or last does not open the player`() =
        runTest {
            libraryPreferences.setNavigation(NavigationPreferences(expandPlayer = true))
            playback.state.value = playing
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.autoExpand.test {
                assertThat(awaitItem()).isFalse()

                playback.playNext(listOf(track()))
                playback.addToQueue(listOf(track()))

                expectNoEvents()
            }
        }

    @Test
    fun `a play that produced no track does not open the player when something is queued later`() =
        runTest {
            libraryPreferences.setNavigation(NavigationPreferences(expandPlayer = true))
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.autoExpand.test {
                assertThat(awaitItem()).isFalse()

                playback.play(listOf(track()))
                playback.addToQueue(listOf(track()))
                playback.state.value = playing

                expectNoEvents()
            }
        }

    private fun track() = Track(MediaId(ServerId("s"), "t1"), "Strobe", emptyList(), 10.minutes)

    @Test
    fun `the waveform shows once computed and never the previous track's`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.uiState.test {
                skipItems(1) // hidden
                playback.state.value = playing
                assertThat((awaitItem() as NowPlayingUiState.Playing).waveform).isNull()

                waveforms.save(playing.item!!.id, 100, listOf(0.2f, 1f))
                assertThat((awaitItem() as NowPlayingUiState.Playing).waveform).containsExactly(0.2f, 1f).inOrder()

                val next = playing.item!!.copy(id = MediaId(ServerId("s"), "t2"))
                playback.state.value = playing.copy(item = next)
                assertThat(expectMostRecentItem().let { (it as NowPlayingUiState.Playing).waveform }).isNull()
            }
        }

    private val playingTrack =
        Track(
            MediaId(ServerId("s"), "t1"),
            "Strobe",
            listOf(Artist(MediaId(ServerId("s"), "ar1"), "deadmau5")),
            10.minutes,
            albumId = MediaId(ServerId("s"), "al1"),
            albumTitle = "For Lack of a Better Name",
            audioFormat = AudioFormat("flac", sampleRateHz = 96_000, bitDepth = 24, bitrateKbps = 1250),
        )

    @Test
    fun `the playing track brings its artists, album and quality once its stream is known`() =
        runTest {
            library.tracks.value = listOf(playingTrack)
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.uiState.test {
                skipItems(1) // hidden
                playback.state.value = playing
                var shown = expectMostRecentItem() as NowPlayingUiState.Playing
                assertThat(shown.artists.map { it.name }).containsExactly("deadmau5")
                assertThat(shown.albumTitle).isEqualTo("For Lack of a Better Name")
                // The stream is not resolved yet: no label rather than a wrong one.
                assertThat(shown.quality).isNull()

                playback.state.value = playing.copy(output = AudioOutputInfo(playedItemId = "s:t1", streamKnown = true))
                shown = awaitItem() as NowPlayingUiState.Playing
                assertThat(shown.quality?.kind).isEqualTo(QualityKind.HiResLossless)

                playback.state.value =
                    playing.copy(
                        output = AudioOutputInfo(playedItemId = "s:t1", streamKnown = true, convertedKbps = 128),
                    )
                shown = awaitItem() as NowPlayingUiState.Playing
                assertThat(shown.quality).isEqualTo(QualityLabel(QualityKind.HighEfficiency, "Opus · 128 kb/s"))

                // Another track, its row not read yet and the output still the previous track's: nothing of it shows.
                val next = playing.item!!.copy(id = MediaId(ServerId("s"), "t2"))
                playback.state.value =
                    playing.copy(
                        item = next,
                        output = AudioOutputInfo(playedItemId = "s:t1", streamKnown = true, convertedKbps = 128),
                    )
                shown = expectMostRecentItem() as NowPlayingUiState.Playing
                assertThat(shown.artists).isEmpty()
                assertThat(shown.albumId).isNull()
                assertThat(shown.quality).isNull()
            }
        }

    @Test
    fun `lyrics in place of the cover stay on for the next track, the label mode is remembered`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            viewModel.uiState.test {
                skipItems(1) // hidden
                playback.state.value = playing
                assertThat((awaitItem() as NowPlayingUiState.Playing).lyricsShown).isFalse()

                viewModel.onAction(NowPlayingAction.ToggleLyrics)
                assertThat((awaitItem() as NowPlayingUiState.Playing).lyricsShown).isTrue()

                playback.state.value =
                    playing.copy(item = playing.item!!.copy(id = MediaId(ServerId("s"), "t2"), title = "Ghosts"))
                assertThat((awaitItem() as NowPlayingUiState.Playing).lyricsShown).isTrue()

                viewModel.onAction(NowPlayingAction.ToggleQualityDetail)
                assertThat((awaitItem() as NowPlayingUiState.Playing).qualityDetail).isTrue()
                assertThat(libraryPreferences.preferences.value.qualityDetail).isTrue()
            }
        }

    @Test
    fun `the track menu downloads the playing track`() =
        runTest {
            library.tracks.value = listOf(playingTrack)
            playback.state.value = playing
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)

            viewModel.onAction(NowPlayingAction.Download)

            assertThat(downloads.downloads.value.map { it.track.id }).containsExactly(playingTrack.id)
        }

    @Test
    fun `a cover tap runs the chosen action, and a plain bar style reads no waveform`() =
        runTest {
            playback.state.value = playing
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            waveforms.save(playing.item!!.id, 100, listOf(0.5f))
            viewModel.uiState.test {
                assertThat((awaitItem() as NowPlayingUiState.Playing).waveform).isNotNull()

                viewModel.onAction(NowPlayingAction.CoverTapped)
                assertThat(playback.commands).containsExactly("togglePlayPause")

                libraryPreferences.setNowPlaying(
                    NowPlayingPreferences(coverTap = CoverTap.ShowLyrics, progress = PlayerProgress.Basic),
                )
                // The settings and then the dropped waveform arrive one after the other.
                var shown = awaitItem() as NowPlayingUiState.Playing
                while (shown.waveform != null) shown = awaitItem() as NowPlayingUiState.Playing
                assertThat(shown.settings.coverTap).isEqualTo(CoverTap.ShowLyrics)

                viewModel.onAction(NowPlayingAction.CoverTapped)
                assertThat((awaitItem() as NowPlayingUiState.Playing).lyricsShown).isTrue()

                libraryPreferences.setNowPlaying(NowPlayingPreferences(coverTap = CoverTap.None))
                skipItems(1)
                viewModel.onAction(NowPlayingAction.CoverTapped)
                assertThat(playback.commands).containsExactly("togglePlayPause")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the stars show only with the switch on and ratings on`() {
        val base = NowPlayingUiState.Playing("t", "a", null, true, 1.minutes, true, true, true)

        assertThat(base.showsRating).isTrue()
        assertThat(base.copy(settings = NowPlayingPreferences(showRating = false)).showsRating).isFalse()
        assertThat(base.copy(ratingMode = RatingMode.Off).showsRating).isFalse()
    }

    @Test
    fun `the track number shows the playing entry's place in the queue only while the setting is on`() =
        runTest {
            val viewModel =
                NowPlayingViewModel(playback, library, userData, lyrics, libraryPreferences, waveforms, downloads)
            playback.queue.value =
                (1..12).map { QueueItem("e$it", MediaId(ServerId("s"), "t$it"), "Track $it", "deadmau5") }
            playback.state.value = playing.copy(currentEntryId = "e3")
            viewModel.uiState.test {
                assertThat((expectMostRecentItem() as NowPlayingUiState.Playing).trackNumber).isNull()

                libraryPreferences.setAdvanced(AdvancedPreferences(showTrackNumber = true))
                assertThat((awaitItem() as NowPlayingUiState.Playing).trackNumber).isEqualTo(QueuePosition(3, 12))

                // The new entry reaches the player state first, then its place in the queue.
                playback.state.value = playing.copy(currentEntryId = "e4")
                var next = awaitItem() as NowPlayingUiState.Playing
                if (next.trackNumber?.number == 3) next = awaitItem() as NowPlayingUiState.Playing
                assertThat(next.trackNumber).isEqualTo(QueuePosition(4, 12))
                cancelAndIgnoreRemainingEvents()
            }
        }
}
