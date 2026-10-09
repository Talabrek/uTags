import java.security.MessageDigest

plugins {
    java
    id("com.gradleup.shadow") version "9.6.0"
}

// ---- build conventions (inlined from the blockworlds-plugins monorepo: build-logic/bw.plugin) ----
// Shared setup for every Blockworlds plugin module.
//
// - Java toolchain from the module's `bw.java` property (gradle.properties next to its build file); default 25,
//   which Paper/Purpur 26.x requires. Older plugins that still target 1.19-1.21 set 17.
// - UTF-8 and -parameters (Brigadier needs parameter names).
// - Replaces ${version}, ${project.version}, ${project.name} and ${project.description} in the plugin
//   descriptors only. A plain string replace, so other ${...} text (e.g. "${price}" in locale files) is untouched.
// - Reproducible jars, plus a <jar>.sha256 next to each, so the panel can match deployed jars to builds.



// Port check: `-Pbw.checkPaper=26.3.build.147-beta` (and/or `-Pbw.checkPurpur=26.3.build.2643-experimental`)
// compiles against that API instead of the one in the build file, on Java 25, with removal warnings shown.
// It tells whether the source still compiles on a new Minecraft version without changing what gets released.
val checkPaper = findProperty("bw.checkPaper") as String?
val checkPurpur = findProperty("bw.checkPurpur") as String?
val apiCheck = checkPaper != null || checkPurpur != null

val bwJava = if (apiCheck) 25 else (findProperty("bw.java") as String?)?.toInt() ?: 25

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(bwJava))
}

if (apiCheck) {
    // Main code only: tests stay on the API their MockBukkit line expects
    configurations.matching { it.name == "compileClasspath" }.configureEach {
        resolutionStrategy.eachDependency {
            if (checkPaper != null && requested.group == "io.papermc.paper" && requested.name == "paper-api") useVersion(checkPaper)
            if (checkPurpur != null && requested.group == "org.purpurmc.purpur" && requested.name == "purpur-api") useVersion(checkPurpur)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:removal", "-Xmaxwarns", "10000"))
    }
    // These classes are Java 25 and built against the newer API: never let them end up in a jar in build/libs
    tasks.withType<AbstractArchiveTask>().configureEach {
        doFirst { throw GradleException("bw.checkPaper/bw.checkPurpur is a compile check only: run compileJava, not build") }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    val tokens = mapOf(
        "\${version}" to project.version.toString(),
        "\${project.version}" to project.version.toString(),
        "\${project.name}" to project.name,
        "\${project.description}" to (project.description ?: ""),
    )
    inputs.properties(tokens.mapKeys { it.key.removePrefix("\${").removeSuffix("}") })
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml", "addon.yml", "bungee.yml", "velocity-plugin.json")) {
        filter { line -> tokens.entries.fold(line) { acc, (k, v) -> acc.replace(k, v) } }
    }
}

// Reproducible jars: the same source always gives the same sha256, so deployed jars can be matched to builds.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Writes build/libs/<jar>.sha256 next to every jar the module produces.
val writeChecksums by tasks.registering {
    description = "Writes a .sha256 file next to each jar in build/libs"
    val libs = layout.buildDirectory.dir("libs")
    doLast {
        libs.get().asFile.listFiles { f -> f.name.endsWith(".jar") }?.forEach { jar ->
            val digest = MessageDigest.getInstance("SHA-256").digest(jar.readBytes())
            val hex = digest.joinToString("") { "%02x".format(it) }
            File(jar.parentFile, jar.name + ".sha256").writeText("$hex  ${jar.name}\n")
        }
    }
}
tasks.named("assemble") { finalizedBy(writeChecksums) }

// ---- this plugin ----
group = "com.blockworlds"
version = "1.4.1"
description = "Tags that players can display in chat"

repositories {
    maven("https://repo.opencollab.dev/main/") // Floodgate API (soft dependency, Bedrock forms)
    maven("https://repo.helpch.at/releases/") // PlaceholderAPI (soft dependency, unlock rules)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // Unit tests of the pure CustomTags logic; the APIs are needed to load the classes
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("net.luckperms:api:5.4")
    testImplementation("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // Real SQLite for the dialect tests; at runtime the server ships its own sqlite-jdbc (it cannot be relocated)
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")
    // MySQL tests run against a throwaway test database (skipped without BW_MYSQL_* in the environment)
    testRuntimeOnly("com.mysql:mysql-connector-j:9.2.0")
    // Fakes of the inventory and the click events for the menu listener tests
    testImplementation("org.mockito:mockito-core:5.23.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("uTags")
    relocate("com.zaxxer.hikari", "com.blockworlds.utags.libs.hikari")
    dependencies {
        // Hikari is the only bundled library; its slf4j-api comes from the server
        exclude(dependency("org.slf4j:.*"))
    }
}

tasks.jar { archiveClassifier.set("plain") }
// uTags-api-<version>.jar: only com.blockworlds.utags.api, for other plugins to compile against (compileOnly)
val apiJar by tasks.registering(Jar::class) {
    archiveBaseName.set("uTags-api")
    archiveClassifier.set("")
    from(sourceSets.main.get().output) { include("com/blockworlds/utags/api/**") }
}
tasks.assemble { dependsOn(apiJar) }
tasks.assemble { dependsOn(tasks.shadowJar) }
