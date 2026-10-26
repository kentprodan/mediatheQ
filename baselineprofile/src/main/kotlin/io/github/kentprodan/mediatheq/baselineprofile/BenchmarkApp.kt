package io.github.kentprodan.mediatheq.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The ".benchmark" build of the app: a separate app on the phone, never the daily one (ADR 0023). */
const val PACKAGE = "io.github.kentprodan.mediatheq.benchmark"

private const val EDIT_TEXT = "android.widget.EditText"
private const val LOCAL_NETWORK_ALLOW = "com.android.permissioncontroller:id/permission_allow_button"
private const val UI_TIMEOUT_MS = 30_000L

// A first sync of the whole library can take a few minutes.
private const val LIBRARY_TIMEOUT_MS = 5 * 60_000L

/**
 * Signs in once, through the onboarding screens, when the app shows them. The server, user and password come from the
 * instrumentation arguments `benchmarkServer`, `benchmarkUser` and `benchmarkPassword`: set them as
 * `android.testInstrumentationRunnerArguments.benchmark*` in the user's own `~/.gradle/gradle.properties`, never in the
 * repository. The app's texts are English only, so they can be found by text.
 */
fun MacrobenchmarkScope.signInIfNeeded() {
    if (device.wait(Until.hasObject(By.text("Library")), 5_000)) return
    // Android 17: on a fresh install the app first asks for local network access (#58), then Android does.
    device.findObject(By.text("Allow access to your home network"))?.let {
        device.findObject(By.text("Allow")).click()
        device.wait(Until.findObject(By.res(LOCAL_NETWORK_ALLOW)), UI_TIMEOUT_MS)?.click()
    }
    if (device.wait(Until.hasObject(By.text("Library")), 5_000)) return
    check(device.wait(Until.hasObject(By.text("Connect")), UI_TIMEOUT_MS)) { "Neither the app nor onboarding showed" }
    device.findObject(By.clazz(EDIT_TEXT)).text = argument("benchmarkServer")
    device.findObject(By.text("Connect")).click()
    check(device.wait(Until.hasObject(By.text("Sign in")), UI_TIMEOUT_MS)) { "The server was not reached" }
    val (user, password) = device.findObjects(By.clazz(EDIT_TEXT))
    user.text = argument("benchmarkUser")
    password.text = argument("benchmarkPassword")
    device.findObject(By.text("Sign in")).click()
    check(device.wait(Until.hasObject(By.text("Library")), UI_TIMEOUT_MS)) { "Sign-in failed" }
}

/** Opens the Library tab and waits for its album grid (after the first sync, which may still run). */
fun MacrobenchmarkScope.openLibrary() {
    device.findObject(By.text("Library")).click()
    check(device.wait(Until.hasObject(By.text("Albums")), UI_TIMEOUT_MS)) { "The Library tab did not open" }
    checkNotNull(device.wait({ list() }, LIBRARY_TIMEOUT_MS)) { "The library stayed empty: is it synced?" }
}

/** Flings the open Library list down and back up. */
fun MacrobenchmarkScope.flingList() {
    val list = checkNotNull(list()) { "No list on screen" }
    // Away from the edges, so a fling never starts a system back gesture.
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** Switches the Library to Songs and waits for its list. */
fun MacrobenchmarkScope.openSongs() {
    device.findObject(By.text("Songs")).click()
    checkNotNull(device.wait({ list() }, UI_TIMEOUT_MS)) { "The songs list did not show" }
}

// The tallest scrollable: the album grid or songs list, not the row of filter chips above it.
private fun MacrobenchmarkScope.list(): UiObject2? =
    device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }

private fun argument(name: String): String =
    requireNotNull(InstrumentationRegistry.getArguments().getString(name)) {
        "Not signed in and no $name: set android.testInstrumentationRunnerArguments.$name in ~/.gradle/gradle.properties"
    }
