plugins {
    alias(libs.plugins.mediatheq.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
}
