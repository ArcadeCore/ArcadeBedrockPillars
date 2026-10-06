rootProject.name = "ArcadeBedrockPillars"

// ArcadeAPI sources live in the sibling ArcadeCore build. Dependency
// substitution wires `org.drappula:ArcadeAPI` to that build's :ArcadeAPI
// project so the addon always compiles against current API sources.
includeBuild("../ArcadeCore") {
    dependencySubstitution {
        substitute(module("org.drappula:ArcadeAPI")).using(project(":ArcadeAPI"))
    }
}
