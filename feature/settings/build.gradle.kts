plugins {
    alias(libs.plugins.mediatheq.android.feature)
    alias(libs.plugins.mediatheq.android.screenshot)
    // Reads the build's licenses report (#70).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kentprodan.mediatheq.feature.settings"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

// The copied-assets list is checked against THIRD_PARTY.md: rerun the tests when it changes.
tasks.withType<Test>().configureEach {
    val notices = rootProject.file("THIRD_PARTY.md")
    inputs.file(notices)
    systemProperty("mediatheq.thirdParty", notices.path)
}
