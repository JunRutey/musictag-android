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

rootProject.name = "musictag-android"
include(":app")
include(":core:tag")
include(":core:net")
include(":core:lyrics")

// Modules are introduced as they land (see docs/ROADMAP in README):
//   :core:lyrics, :core:data, :core:media, :core:providers
