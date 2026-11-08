plugins {
    java
}

group = "com.extantape"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.onarandombox.com/multiverse-releases/")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.GriefPrevention") }
    }
}

// Built against the oldest supported Paper, so nothing newer than 26.1 sneaks in.
val paperApi = "io.papermc.paper:paper-api:26.1.2.build.74-stable"
extra["paperApi"] = paperApi

dependencies {
    compileOnly(paperApi)
    // Optional integrations. Their classes are only touched when the plugin is installed.
    compileOnly("org.mvplugins.multiverse.inventories:multiverse-inventories:5.3.6") { isTransitive = false }
    compileOnly("com.github.GriefPrevention:GriefPrevention:16.18.4") { isTransitive = false }

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    // Shows Paper API deprecations as they arrive, so they can be dealt with before removal.
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("Portmanteau")
    from("LICENSE")
}
