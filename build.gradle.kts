plugins {
    base
    id("fabric-loom") version "1.10.5" apply false
}

val piriVersion = providers.gradleProperty("piriVersion").get()

allprojects {
    group = "jp.pirijuggler"
    version = piriVersion
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("piri.specRoot", rootProject.projectDir.absolutePath)
        inputs.files(rootProject.file("SPEC.md"), rootProject.file("docs/spec-lock.json"))
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

tasks.register("test") { dependsOn(subprojects.map { "${it.path}:test" }) }
tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }

tasks.register("regeneratePiriAssets") { dependsOn(":asset-tools:generateImages") }

tasks.register<Copy>("packagePiriJars") {
    dependsOn("build")
    from("paper/build/libs") { include("piri-juggler-paper-${project.version}.jar") }
    from("fabric/build/libs") { include("piri-juggler-fabric-${project.version}.jar") }
    into(layout.projectDirectory.dir("dist"))
}
