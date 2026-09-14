dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}
tasks.test { inputs.dir(rootProject.file("fabric/src/main/resources/assets/piri/textures")) }

tasks.register<JavaExec>("generateImages") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("jp.pirijuggler.assets.AssetGenerator")
    args(rootProject.projectDir.absolutePath)
    systemProperty("java.awt.headless", "true")
}
