plugins { `java-library` }

base { archivesName.set("piri-juggler-common") }

dependencies { api("com.google.code.gson:gson:2.10.1") }

tasks.register<JavaExec>("exportSkillStopEconomy") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("jp.pirijuggler.common.reel.SkillStopEconomyExport")
    maxHeapSize = "768m"
    args(rootProject.layout.buildDirectory.file("skill-stop-production-models.json").get().asFile.absolutePath)
    doFirst { rootProject.layout.buildDirectory.get().asFile.mkdirs() }
}

tasks.register<JavaExec>("exportSkillStopPhase06Vectors") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("jp.pirijuggler.common.reel.SkillStopPhase06Vectors")
    args(rootProject.layout.buildDirectory.file("skill-stop-phase06-vectors.json").get().asFile.absolutePath)
    doFirst { rootProject.layout.buildDirectory.get().asFile.mkdirs() }
}
