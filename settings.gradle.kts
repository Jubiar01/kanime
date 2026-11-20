pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KAnime"
include(":app")
if (file("kanikoto-plugin").exists()) {
    include(":kanikoto-plugin")
}
if (file("kanimeheaven-plugin").exists()) {
    include(":kanimeheaven-plugin")
}
if (file("kopensubtitle-plugin").exists()) {
    include(":kopensubtitle-plugin")
}
if (file("kreanime-plugin").exists()) {
    include(":kreanime-plugin")
}
