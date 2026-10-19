pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Settings plugins cannot read the version catalog, so this version lives here.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "mediatheQ"
include(":app")
include(":core:model")
include(":core:common")
include(":core:testing")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:network")
include(":core:server")
include(":core:datastore")
include(":core:data")
include(":provider:jellyfin")
