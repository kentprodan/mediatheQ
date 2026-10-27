plugins {
    alias(libs.plugins.mediatheq.android.test)
    alias(libs.plugins.baselineprofile)
}

// Generates the app's baseline profile and measures it on a real phone (#71, ADR 0023). Runs against the
// ".benchmark" build of :app, a separate app that stays installed: the phone's own mediatheQ is never touched.
android {
    namespace = "io.github.kentprodan.mediatheq.baselineprofile"
    targetProjectPath = ":app"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Macrobenchmarks install and drive the app under test from this module's own process.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
