plugins { `java-library` }

base { archivesName.set("piri-runtime-test-paper") }
repositories { maven("https://repo.papermc.io/repository/maven-public/") }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly(project(":paper"))
    compileOnly(project(":common"))
}
