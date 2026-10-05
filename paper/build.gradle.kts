plugins { `java-library` }

base { archivesName.set("piri-juggler-paper") }

repositories { maven("https://repo.papermc.io/repository/maven-public/") }

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation("io.netty:netty-codec-http:4.1.97.Final")
    compileOnly("org.yaml:snakeyaml:2.2")
    testImplementation("org.yaml:snakeyaml:2.2")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        showStandardStreams = true
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    from(project(":fabric").file("src/main/resources/assets/piri/textures")) { into("mobile-assets/textures") }
    from(rootProject.file("user-audio")) { include("*.ogg"); into("mobile-assets/sounds") }
    from(project(":fabric").file("src/main/resources/assets/piri/sounds")) { include("*.ogg"); into("mobile-assets/sounds") }
    from(project(":fabric").file("src/main/resources/assets/piri/client-ui.json")) { into("mobile-assets") }
    from({ configurations.runtimeClasspath.get().filter { it.name.startsWith("sqlite-jdbc-") || it.name.startsWith("netty-codec-http-") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/**/module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
