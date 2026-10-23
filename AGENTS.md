# AGENTS.md

Paper 1.21.11 addon (`bedrock-pillars`) for ArcadeCore. Java 21 toolchain. See `README.md` for behavior/deviation history.

## Setup prerequisite

- Sibling checkout `../ArcadeCore` is required. `settings.gradle.kts` composite-build substitutes `org.drappula:ArcadeAPI` with `../ArcadeCore`'s `:ArcadeAPI` project — the build fails without it.

## Commands

- `./gradlew build` — compile + test + thin jar.
- `./gradlew test` — unit suite (JUnit 5 + Mockito + MockBukkit, base class `PluginTest`).
- `./gradlew runServer` — interactive local Paper 1.21.11 server. Run `scripts/prepare-run.sh` first.
- `./gradlew smokeTest` — boots real Paper in tmux, asserts ArcadeCore + addon enable, runs `bp` from console, stops. First run takes ~5 min (Paper download); bootstrap once with `./gradlew runServer` (Ctrl-C after `Done`). Refuses to boot with < 2G free RAM.

## Dependency / classpath quirks

- `paper-api` and `ArcadeAPI` are `compileOnly` — ArcadeCore provides both at runtime (`paper-plugin.yml`: `join-classpath: true`). Keep the jar thin; don't switch them to `implementation`.
- Tests redeclare both as `testImplementation`, with **MockBukkit first** in `build.gradle.kts` — it ships its own paper-api and must win classpath order.
- `paper-plugin.yml`: `load: POSTWORLD`, ArcadeCore `BEFORE`, required.

## Memory caps (do not raise above 2G)

- `gradle.properties`: `-Xmx1g`, `workers.max=2`. `runServer`/smoke cap the server at `-Xms1G -Xmx2G`. Unbounded builds + a Paper server on one box have OOM-crashed this machine before.

## Architecture

- `src/main/java/.../arcadeBedrockPillars/`: `ArcadeBedrockPillars` (entrypoint), `BedrockPillarsGame` (game id `bedrock-pillars`; min/max from config), `BedrockPillarsListener`, `BedrockPillarsCommand` (`/bp join|leave`, op-only `/bp debug win|lose|end`), `ItemDropTask`, `PoolLavaTask`, `Messages`.
- `createArena` returns null — matches run on hand-built pool maps (`/arcade map`), never generated arenas. No enabled map with enough spawns = core fails start with `map-unavailable`.
- Map options (`BedrockPillarsGame.getMapConfigOptions`): `lava-blocks-rise-per-minute` (default 2, 0 disables), `lava-start-y` (64), `border-size` (0 = untouched), `item-interval-seconds` (0 = fall back to global `item-interval-seconds`, default 5).
- `PoolLavaTask` restores every placed cell at match end because pool worlds persist. `border-size` shrinks the world border for everyone in that world — prefer a dedicated world per map.
- Version is triplicated — `build.gradle.kts`, `gradle.properties`, `paper-plugin.yml` (all `1.5.1` now); bump all three together.

## Test quirks

- `PluginTest.bootPlugin` boots MockBukkit, registers a mocked `ArcadeAPI`, loads the real plugin. `ArcadeBedrockPillars` must stay non-`final` or `MockBukkit.load` fails.
- Block-level tests use the custom tall `arenaWorld` (`-64..320`) — MockBukkit default worlds cap at y=128.
- `run/` is gitignored run-paper output; `scripts/prepare-run.sh` copies only the ArcadeCore jar into `run/plugins` — run-paper installs this project's own jar itself, copying it manually double-loads the plugin.
