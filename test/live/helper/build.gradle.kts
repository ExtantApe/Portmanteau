plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.GriefPrevention") }
    }
}

dependencies {
    compileOnly(rootProject.extra["paperApi"] as String)
    compileOnly(rootProject)
    compileOnly("com.github.GriefPrevention:GriefPrevention:16.18.4") { isTransitive = false }
}

tasks.jar {
    archiveBaseName.set("PortmanteauLiveTestHelper")
}
