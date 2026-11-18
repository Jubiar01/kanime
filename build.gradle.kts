// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Keep unqualified `./gradlew installDebug` / `installRelease` scoped to the main `:app` module.
// Plugins can still be installed explicitly, e.g. `./gradlew :kopensubtitle-plugin:installDebug`.
val requestedTasks = gradle.startParameter.taskNames
val unqualifiedInstalls = requestedTasks.filter { it == "installDebug" || it == "installRelease" }
if (unqualifiedInstalls.isNotEmpty()) {
    val exclusions = gradle.startParameter.excludedTaskNames.toMutableSet()
    for (taskName in unqualifiedInstalls) {
        for (sub in subprojects) {
            if (sub.path == ":app") continue
            val explicitNames = setOf("${sub.path}:$taskName", "${sub.path.removePrefix(":")}:$taskName")
            if (requestedTasks.none { it in explicitNames }) {
                exclusions.add("${sub.path}:$taskName")
            }
        }
    }
    gradle.startParameter.setExcludedTaskNames(exclusions)
}
