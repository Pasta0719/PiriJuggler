plugins { id("fabric-loom") }

base { archivesName.set("piri-juggler-fabric") }

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val yarnVersion = providers.gradleProperty("yarnVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnVersion:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation(project(":common"))
    testImplementation(project(":paper"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    inputs.property("fabricLoaderVersion", fabricLoaderVersion)
    inputs.property("fabricApiVersion", fabricApiVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "fabricLoaderVersion" to fabricLoaderVersion,
            "fabricApiVersion" to fabricApiVersion
        )
    }
    val userAudio = rootProject.file("user-audio")
    val names = listOf("notice", "notice_strong", "tenpai", "bet", "lever", "stop", "payout", "error", "bonus_start", "bonus_end")
    from(userAudio) {
        include(names.map { "$it.ogg" })
        into("assets/piri/sounds")
    }
    eachFile {
        if (path.startsWith("assets/piri/sounds/") && userAudio.resolve(name).isFile && file.canonicalFile != userAudio.resolve(name).canonicalFile) exclude()
    }
}

tasks.jar {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
}
