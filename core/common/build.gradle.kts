plugins {
    alias(libs.plugins.mediatheq.jvm.library)
    // Reads the build's licenses report (#70, shared with the TV in #243).
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Pure helpers both UI layers read (ADR 0038): the quality label needs the audio format.
    api(projects.core.model)
    // Qualifier annotations only; Hilt reads javax.inject qualifiers.
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The copied-assets list is checked against THIRD_PARTY.md: rerun the tests when it changes.
tasks.withType<Test>().configureEach {
    val notices = rootProject.file("THIRD_PARTY.md")
    inputs.file(notices).withPathSensitivity(PathSensitivity.RELATIVE)
    // Relative to the module, the tests' working directory, so the build cache key does not depend on this machine.
    systemProperty("mediatheq.thirdParty", notices.relativeTo(projectDir).invariantSeparatorsPath)
}
