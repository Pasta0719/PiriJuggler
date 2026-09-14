pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "piri-juggler"
include("common", "paper", "fabric", "asset-tools")

// Runtime-only helpers are opt-in and never enter production artifacts.
if (providers.gradleProperty("runtimeAcceptance").isPresent) {
    include("runtime-test-client", "runtime-test-paper")
    project(":runtime-test-client").projectDir = file("runtime-test-support/client")
    project(":runtime-test-paper").projectDir = file("runtime-test-support/paper")
}
