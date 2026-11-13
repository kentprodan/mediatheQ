package io.github.kentprodan.mediatheq.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a TV module ships the phone's UI (ADR 0037, 0038): `:core:designsystem`, `:core:ui`, a `:feature:*`
 * module or `androidx.compose.material3`. Reads what the release build ships, so debug-only tooling (ui-tooling brings
 * material3 for previews) does not count.
 */
abstract class CheckTvDependenciesTask : DefaultTask() {
    /** Every component of the release runtime classpath: `:project:path` or `group:module`. */
    @get:Input
    abstract val components: ListProperty<String>

    @TaskAction
    fun check() {
        val forbidden = components.get().filter(::isPhoneUi).sorted()
        if (forbidden.isNotEmpty()) {
            throw GradleException("A TV module must not depend on the phone UI (ADR 0038): ${forbidden.joinToString()}")
        }
    }

    companion object {
        fun isPhoneUi(component: String): Boolean =
            component == ":core:designsystem" ||
                component == ":core:ui" ||
                component.startsWith(":feature:") ||
                component.startsWith("androidx.compose.material3:")
    }
}

/** Registers the check on the release runtime classpath and runs it before every build of this module. */
internal fun Project.configureTvDependencyCheck() {
    val check =
        tasks.register("checkTvDependencies", CheckTvDependenciesTask::class.java) {
            components.set(
                provider { configurations.getByName("releaseRuntimeClasspath") }
                    .flatMap { it.incoming.resolutionResult.rootComponent }
                    .map { root -> root.allComponents().map(::name) },
            )
        }
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(check) }
}

private fun name(component: ResolvedComponentResult): String =
    when (val id = component.id) {
        is ProjectComponentIdentifier -> id.projectPath
        is ModuleComponentIdentifier -> "${id.group}:${id.module}"
        else -> id.displayName
    }

private fun ResolvedComponentResult.allComponents(): List<ResolvedComponentResult> {
    val seen = linkedSetOf(this)
    val queue = ArrayDeque(listOf(this))
    while (queue.isNotEmpty()) {
        queue
            .removeFirst()
            .dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .map { it.selected }
            .filter(seen::add)
            .forEach(queue::addLast)
    }
    return seen.toList()
}
