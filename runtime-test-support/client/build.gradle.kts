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
require(scenario in setOf("normal", "mismatch", "phase02-create", "phase02-owner", "phase02-other", "phase03-ui", "phase04-reels", "phase05-game"))
val phase = providers.gradleProperty("runtimeEvidencePhase").orElse(if (scenario.startsWith("phase02")) "PHASE_02" else "PHASE_01").get()
require(phase in setOf("PHASE_01", "PHASE_02", "PHASE_02_PHASE01_REGRESSION", "PHASE_03", "PHASE_03_PHASE01_REGRESSION", "PHASE_04", "PHASE_04_PHASE01_REGRESSION", "PHASE_05", "PHASE_05_PHASE01_REGRESSION", "PHASE_05_REEL_REGRESSION", "PHASE_05_BAR_BIG", "PHASE_05_BAR_REG"))
val runtimeRun = providers.gradleProperty("runtimeRun").orElse("current").get()
require(runtimeRun.matches(Regex("[a-zA-Z0-9_-]+")))
val evidenceDirectory = rootProject.file(if (phase == "PHASE_02" || phase == "PHASE_03" || (phase == "PHASE_04" || phase == "PHASE_05_REEL_REGRESSION") || (phase == "PHASE_05" || phase.startsWith("PHASE_05_BAR_"))) "runtime-evidence/$phase/attempts/$runtimeRun/$scenario" else "runtime-evidence/$phase/$scenario")
val player = if (scenario == "phase02-other") "PiriRuntimeTest2" else "PiriRuntimeTest"
val port = when (phase) {
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
            vmArgs("-Dpiri.runtime.scenario=$scenario", "-Dpiri.runtime.clientResult=${evidenceDirectory.resolve("client-result.json").absolutePath}", "-Xmx2G")
        }
    }
}

tasks.named("runClient") { dependsOn(":fabric:remapJar") }
tasks.matching { it.name == "generateRemapClasspath" }.configureEach { dependsOn(":fabric:remapJar") }
