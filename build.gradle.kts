plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

import java.time.Duration

group = "org.drappula"
version = "1.5.1"
description = "Bedrock Pillars minigame for ArcadeCore"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Provided at runtime by ArcadeCore (paper-plugin.yml: join-classpath: true),
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
    testImplementation("net.kyori:adventure-text-logger-slf4j:4.24.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        // Local Paper 1.21.11 test server with the plugin installed.
        // ArcadeCore must be present too: copy its shadow jar into run/plugins
        // (see scripts/prepare-run.sh) before launching.
        // Memory is deliberately capped at 1G: a 2G server plus Gradle workers
        // on a loaded desktop has OOM-crashed this machine before.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms512M", "-Xmx1G")
    }

    // Real smoke test: boots Paper via runServer inside tmux with ArcadeCore
    // + this addon installed, asserts both enable cleanly, exercises the /bp
    // console command, stops. Usage: ./gradlew smokeTest
    register("smokeTest", Exec::class) {
        group = "verification"
        description = "Boot a real Paper server in tmux and smoke-test ArcadeBedrockPillars enable + console command."
        dependsOn("jar")
        commandLine("bash", rootProject.file("scripts/smoke-test.sh").absolutePath)
        timeout.set(Duration.ofMinutes(15))
    }
}
