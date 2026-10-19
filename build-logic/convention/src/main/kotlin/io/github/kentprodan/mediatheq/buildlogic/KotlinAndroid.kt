package io.github.kentprodan.mediatheq.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

/** SDK levels and JDK toolchain shared by every Android module (ADR 0003, ADR 0010). */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = libs.intVersion("compileSdk")
    android.defaultConfig.minSdk = libs.intVersion("minSdk")
    configureKotlin()
}

/** JDK toolchain for every Kotlin module. */
internal fun Project.configureKotlin() {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(libs.intVersion("jdk"))
    }
}
