package io.github.kentprodan.mediatheq.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to Home without and with the baseline profile (#71). Run after the generator (which signs in):
 * `.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            // Home of a signed-in app, not onboarding: otherwise the numbers measure another screen.
            check(device.wait(Until.hasObject(By.text("Library")), 5_000)) { "Not signed in: run the generator first" }
        }
}
