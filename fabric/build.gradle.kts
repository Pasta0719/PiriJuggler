plugins { id("fabric-loom") }

base { archivesName.set("piri-juggler-fabric") }

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val yarnVersion = providers.gradleProperty("yarnVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
val fabricApiRequirement = providers.gradleProperty("fabricApiRequirement").get()

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
    inputs.property("fabricApiRequirement", fabricApiRequirement)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "fabricLoaderVersion" to fabricLoaderVersion,
            "fabricApiRequirement" to fabricApiRequirement
        )
    }
    val userAudio = rootProject.file("user-audio")
    val names = listOf(
        "notice", "notice_strong", "tenpai", "bet", "lever", "stop", "payout", "error",
        "bonus_start", "bonus_end", "big_bgm", "reg_bgm", "god_freeze",
        "juggler_god_notice", "juggler_god_notice_strong", "juggler_god_tenpai",
        "juggler_god_bet", "juggler_god_lever", "juggler_god_stop", "juggler_god_payout",
        "juggler_god_error", "juggler_god_bonus_start", "juggler_god_bonus_end",
        "juggler_god_big_bgm", "juggler_god_reg_bgm",
        "juggler_god_god_freeze", "juggler_god_god_stop_1", "juggler_god_god_stop_2",
        "juggler_god_god_stop_3", "juggler_god_god_bonus_start", "juggler_god_god_big_bgm"
    )
    from(userAudio) {
        include(names.map { "$it.ogg" })
        into("assets/piri/sounds")
    }
    eachFile {
        if (path.startsWith("assets/piri/sounds/") && userAudio.resolve(name).isFile && file.canonicalFile != userAudio.resolve(name).canonicalFile) exclude()
    }

    val userGodLcd = rootProject.file("user-god-lcd")
    from(userGodLcd) {
        include("scenes.json")
        into("assets/piri/god_lcd")
    }
    from(userGodLcd.resolve("textures")) {
        include("**/*.png")
        into("assets/piri/textures/god_lcd")
    }
    val userGodReels = rootProject.file("user-god-reels")
    from(userGodReels) {
        include("**/*.png")
        into("assets/piri/textures/god_reels")
    }

}

tasks.jar {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
}
