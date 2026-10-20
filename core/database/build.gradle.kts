plugins {
    alias(libs.plugins.mediatheq.android.library)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.room3)
}

android {
    namespace = "io.github.kentprodan.mediatheq.core.database"
    testOptions.unitTests.isIncludeAndroidResources = true
    // Robolectric on JDK 21 (same as the Compose convention).
    testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
}

// The migration test reads the exported schemas as assets (MigrationTestHelper, Robolectric).
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.room3.testing)
    testImplementation(libs.robolectric)
}
