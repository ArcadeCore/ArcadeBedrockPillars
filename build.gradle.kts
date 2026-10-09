plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.3"
}

import java.time.Duration

group = "org.drappula"
version = "1.5.1"
description = "Bedrock Pillars minigame for ArcadeCore"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.github.cryptomorin:XSeries:13.7.1")
    // Provided at runtime by ArcadeCore (plugin.yml depend),
    // so compileOnly keeps the jar thin like the 1.0.0 release.
    compileOnly("org.drappula:ArcadeAPI:1.0.0")

    // compileOnly paper-api is absent from the test runtime classpath,
    // so tests that touch Bukkit/Adventure classes need it explicitly.
    // MockBukkit first: it ships its own paper-api and must win classpath order.
    // compileOnly deps stay out of the test classpath, so tests that touch
    // ArcadeAPI classes need it explicitly (same pattern as paper-api below).
    testImplementation("org.drappula:ArcadeAPI:1.0.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Production code must load on Java 8 servers; tests use the newer JDK.
tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("com.cryptomorin.xseries", "org.drappula.arcadeBedrockPillars.libs.xseries")
    }
    jar {
        archiveClassifier.set("thin")
    }

    test {
        useJUnitPlatform()
    }

    // Real smoke test: boots Paper via runServer inside tmux with ArcadeCore
    // + this addon installed, asserts both enable cleanly, exercises the /bp
    // console command, stops. Usage: ./gradlew smokeTest
    register("smokeTest", Exec::class) {
        group = "verification"
        description = "Boot a real Paper server in tmux and smoke-test ArcadeBedrockPillars enable + console command."
        dependsOn("shadowJar")
        commandLine("bash", rootProject.file("scripts/smoke-test.sh").absolutePath)
        timeout.set(Duration.ofMinutes(15))
    }
}
