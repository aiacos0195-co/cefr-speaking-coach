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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx (offline neural TTS) is served here. Its jitpack.yml
        // installs the prebuilt Android AAR, so JitPack does not recompile the
        // native code, it just re-hosts it.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CEFR Speaking Coach"
include(":app")
