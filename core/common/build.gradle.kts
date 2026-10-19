plugins {
    alias(libs.plugins.mediatheq.jvm.library)
}

dependencies {
    // Qualifier annotations only; Hilt reads javax.inject qualifiers.
    api(libs.javax.inject)
}
