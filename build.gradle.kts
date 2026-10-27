// Puts every plugin on the root classpath once, so all modules share one classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.spotless)
}

// One formatting config for the whole repo, including build-logic.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}
