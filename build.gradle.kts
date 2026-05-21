plugins {
    java
}

group = "com.thebridge"
version = "1.3.8"
description = "TheBridge"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://ci.athion.net/job/FastAsyncWorldEdit/ws/mvn/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.12.3")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.12.3")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(project.properties)
    }
}

tasks.jar {
    archiveBaseName.set("TheBridge")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
}
