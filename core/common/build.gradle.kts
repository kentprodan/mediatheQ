plugins {
    alias(libs.plugins.mediatheq.jvm.library)
}

dependencies {
    // Pure helpers both UI layers read (ADR 0038): the quality label needs the audio format.
    api(projects.core.model)
    // Qualifier annotations only; Hilt reads javax.inject qualifiers.
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
