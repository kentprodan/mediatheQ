package io.github.kentprodan.mediatheq.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile (#71): cold start to Home, then the Library album grid and songs list scrolled.
 * Run with `.\gradlew.bat :app:generateBaselineProfile` on the Pixel 9; the profile lands in
 * `app/src/release/generated/baselineProfiles/` and is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndLibraryScroll() =
        rule.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()
            signInIfNeeded()
            // Not skipped when it fails: a startup-only profile would look like a success.
            openLibrary()
            flingList()
            openSongs()
            flingList()
        }
}
