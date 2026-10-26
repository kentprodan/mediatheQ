package io.github.kentprodan.mediatheq.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame timing while flinging the Library album grid and songs list, with the baseline profile (#71). */
@RunWith(AndroidJUnit4::class)
class LibraryScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun albumGrid() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startActivityAndWait()
                openLibrary()
            },
        ) { flingList() }

    @Test
    fun songsList() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startActivityAndWait()
                openLibrary()
                openSongs()
            },
        ) { flingList() }
}
