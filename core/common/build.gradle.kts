plugins {
    alias(libs.plugins.mediatheq.jvm.library)
}

dependencies {
    // Qualifier annotations only; Hilt reads javax.inject qualifiers.
    implementation(libs.javax.inject)
}
