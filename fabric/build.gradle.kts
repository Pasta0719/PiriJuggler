plugins { id("fabric-loom") }

base { archivesName.set("piri-juggler-fabric") }

dependencies {
    minecraft("com.mojang:minecraft:1.21")
    mappings("net.fabricmc:yarn:1.21+build.9:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.14")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21")
    implementation(project(":common"))
    testImplementation(project(":paper"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
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
