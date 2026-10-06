plugins { id("fabric-loom") }

base { archivesName.set("piri-runtime-test-client") }

dependencies {
    minecraft("com.mojang:minecraft:1.21")
    mappings("net.fabricmc:yarn:1.21+build.9:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.14")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21")
    compileOnly(project(":common"))
    compileOnly(project(":fabric", "namedElements"))
    modRuntimeOnly(files(rootProject.file("fabric/build/libs/piri-juggler-fabric-1.0.0.jar")))
}

val scenario = providers.gradleProperty("runtimeScenario").orElse("normal").get()
require(scenario in setOf("normal", "mismatch", "phase02-create", "phase02-owner", "phase02-other", "phase03-ui", "phase04-reels", "phase05-game", "phase12-owner", "phase12-spectator", "phase13-world", "phase13-owner", "phase13-spectator", "phase14-perf", "god02-main", "next02-main", "next04-main", "next05-game", "skill02-main", "skill03-main", "skill05-main", "skill05-spectator", "skill06-main", "skill06-spectator", "pachinko10-owner", "pachinko10-peer", "mobile-e2e"))
val phase = providers.gradleProperty("runtimeEvidencePhase").orElse(if (scenario.startsWith("phase02")) "PHASE_02" else "PHASE_01").get()
require(phase in setOf("PHASE_01", "PHASE_02", "PHASE_02_PHASE01_REGRESSION", "PHASE_03", "PHASE_03_PHASE01_REGRESSION", "PHASE_04", "PHASE_04_PHASE01_REGRESSION", "PHASE_05", "PHASE_05_PHASE01_REGRESSION", "PHASE_05_REEL_REGRESSION", "PHASE_05_BAR_BIG", "PHASE_05_BAR_REG", "PHASE_12", "PHASE_13", "PHASE_14", "GOD_PHASE_02", "NEXT_PHASE_02", "NEXT_PHASE_04", "NEXT_PHASE_05", "SKILL_STOP_PHASE_02", "SKILL_STOP_PHASE_03", "SKILL_STOP_PHASE_05", "SKILL_STOP_PHASE_06", "PACHINKO_PHASE_10", "MOBILE_E2E"))
val runtimeRun = providers.gradleProperty("runtimeRun").orElse("current").get()
require(runtimeRun.matches(Regex("[a-zA-Z0-9_-]+")))
val evidenceDirectory = rootProject.file(if (phase == "MOBILE_E2E") "runtime-evidence/$phase/attempts/$runtimeRun/$scenario" else if (phase == "PACHINKO_PHASE_10") "runtime-evidence/$phase/attempts/$runtimeRun/$scenario" else if (phase == "SKILL_STOP_PHASE_06" || phase == "SKILL_STOP_PHASE_05" || phase == "SKILL_STOP_PHASE_03" || phase == "SKILL_STOP_PHASE_02" || phase == "GOD_PHASE_02" || phase == "NEXT_PHASE_02" || phase == "NEXT_PHASE_04" || phase == "NEXT_PHASE_05") "runtime-evidence/$phase/attempts/$runtimeRun" else if (phase == "PHASE_02" || phase == "PHASE_03" || phase == "PHASE_12" || phase == "PHASE_13" || phase == "PHASE_14" || (phase == "PHASE_04" || phase == "PHASE_05_REEL_REGRESSION") || (phase == "PHASE_05" || phase.startsWith("PHASE_05_BAR_"))) "runtime-evidence/$phase/attempts/$runtimeRun/$scenario" else "runtime-evidence/$phase/$scenario")
val player = when {
    scenario == "pachinko10-peer" -> "PiriRuntimeTest2"
    scenario == "skill06-spectator" || scenario == "skill05-spectator" -> "PiriRuntimeTest2"
    scenario == "phase02-other" || scenario == "phase12-spectator" || scenario == "phase13-spectator" -> "PiriRuntimeTest2"
    phase == "PHASE_12" && scenario == "mismatch" -> "PiriMismatch"
    else -> "PiriRuntimeTest"
}
val port = when (phase) {
    "MOBILE_E2E" -> "25605"
    "PACHINKO_PHASE_10" -> "25604"
    "SKILL_STOP_PHASE_06" -> "25603"
    "SKILL_STOP_PHASE_05" -> "25602"
    "SKILL_STOP_PHASE_03" -> "25601"
    "SKILL_STOP_PHASE_02" -> "25600"
    "NEXT_PHASE_05" -> "25599"
    "NEXT_PHASE_04" -> "25598"
    "NEXT_PHASE_02" -> "25597"
    "GOD_PHASE_02" -> "25596"
    "PHASE_14" -> "25592"\n    "PHASE_13" -> "25591"
    "PHASE_12" -> "25590"
    "PHASE_05", "PHASE_05_BAR_BIG", "PHASE_05_BAR_REG" -> "25589"
    "PHASE_04", "PHASE_05_REEL_REGRESSION" -> "25588"
    "PHASE_03" -> "25587"
    "PHASE_02" -> "25586"
    else -> "25585"
}

loom {
    runs {
        named("client") {
            runDir("../../runtime-evidence/$phase/work/client-$scenario")
            programArgs("--username", player, "--quickPlayMultiplayer", "127.0.0.1:$port", "--width", "960", "--height", "540")
            vmArgs("-Dpiri.runtime.scenario=$scenario", "-Dpiri.runtime.clientResult=${evidenceDirectory.resolve("client-result.json").absolutePath}", if (phase == "SKILL_STOP_PHASE_05") "-Xmx1G" else "-Xmx2G")
        }
    }
}

tasks.named("runClient") { dependsOn(":fabric:remapJar") }
tasks.matching { it.name == "generateRemapClasspath" }.configureEach { dependsOn(":fabric:remapJar") }
