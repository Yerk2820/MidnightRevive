plugins {
    java
}

group = "dev.yerk2820"
version = "4.4.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Pinned to a stable 26.3 build (see https://jd.papermc.io/paper/26.3/).
    compileOnly("io.papermc.paper:paper-api:26.3.build.242-stable")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
