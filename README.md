# ArcadeBedrockPillars

Bedrock Pillars minigame addon for [ArcadeCore](../ArcadeCore). Matches run
on hand-built pool maps; periodic item drops and a map-configured rising
lava level force the fight until eliminations decide the match.

This project started as a source recreation of `ArcadeBedrockPillars-1.0.0.jar`
(CFR decompile + bytecode verification). It has since diverged (see
"Deviations from the 1.0.0 jar"), so a recompile no longer reproduces the
original jar.

## Supported versions

One jar runs on Minecraft 1.8 to 26.3 on Spigot, Paper and forks. It is
compiled with `javac --release 8` against `spigot-api` 1.8.8 (`compileOnly`),
so it needs Java 8 or newer at runtime. Unit tests still run on JDK 21 with
MockBukkit v1.21.

Verified: ArcadeCore + ArcadeBedrockPillars + ArcadeFFA + ArcadeHub boot on
Paper 1.8.8, 1.12.2, 1.16.5, 1.20.4, 1.21.4 and 26.3, and the command smoke
test runs, with 0 errors in the log. Not verified: a full bot-driven match
(queue, countdown, lava, drops, win) on each version. Treat gameplay on any
version you have not played yourself as untested, and read "Per-version
caveats" below.

## Install

1. Put the ArcadeCore jar (`ArcadePlugin-1.0.0-all.jar`) in `plugins/`. This
   addon needs it: `plugin.yml` has `depend: [ArcadeCore]`.
2. Put the addon jar (`ArcadeBedrockPillars-<version>.jar`, built with
   `./gradlew shadowJar`) in `plugins/`.
3. Start the server. `config.yml` is written on first boot.
4. Create and enable at least one pool map (see "Map setup"). No match can
   start without one.

The addon is built against ArcadeAPI 1.1.0, which breaks 1.0.0 (see
"Deviations from the 1.0.0 jar"), so use an ArcadeCore build that ships 1.1.0.

## Commands

All commands are `/bp`, a plain Bukkit executor with tab completion.

- `/bp join`: join the Bedrock Pillars queue (players only).
- `/bp leave`: leave the queue (players only).
- `/bp chaos`: spectators of a Bedrock Pillars match only. Drops a random item
  from the pool on a random alive player. Limited by `chaos.cooldown-seconds`
  (per spectator) and `chaos.max-per-match`.
- `/bp debug win|lose|end`: test commands for someone currently in a Bedrock
  Pillars match. `win` declares you winner and ends the match, `lose`
  eliminates you, `end` ends it with no winners. Needs the
  `bedrockpillars.debug` permission. It is not declared in `plugin.yml`, so
  only ops have it unless you grant it. `debug` only shows up in tab
  completion for senders who have the permission.

## Config

`plugins/ArcadeBedrockPillars/config.yml` (packaged default in
`src/main/resources/config.yml`):

- `min-players` (1) and `max-players` (20): queue size limits.
- `item-interval-seconds` (5): seconds between drops. A map can override it
  with its own `item-interval-seconds` option; 0 on the map means use this
  value.
- `items`: drop pool, each entry `MATERIAL` or `MATERIAL:amount`. Names are the
  modern ones (`OAK_PLANKS`, `WHITE_WOOL`); XSeries maps them to the running
  server version. Amount is capped at the item's stack size. Arrows, snowballs
  and eggs default to 8 per drop, everything else to 1. Unknown or
  unsupported names are skipped without an error.
- `chaos.cooldown-seconds` (20) and `chaos.max-per-match` (10).

## Map setup

Matches run on hand-built pool maps managed by ArcadeCore. Run these as a
player (`addspawn` uses your position and is rejected from the console):

```
/arcade map create <id> bedrock-pillars
/arcade map addspawn <id>
/arcade map enable <id>
```

A pool map needs at least `max-players` spawns, real pillars under the
spawns, and `lava-start-y` below them. Per-map options are set with
`/arcade map config <map> set <key> <value>`:

| Option | Default | Range | Meaning |
|---|---|---|---|
| `lava-blocks-rise-per-minute` | 2 | 0..60 | Lava layers per minute. 0 disables the rise. |
| `lava-start-y` | 64 | -64..320 | Y of the first lava layer. |
| `border-size` | 0 | 0..60000000 | World border size during the match. 0 leaves the border alone. |
| `item-interval-seconds` | 0 | 0..3600 | Per-map drop interval. 0 uses the global config value. |

The `lava-start-y` range is declared as -64..320, which assumes 1.18+ world
height. On an older server the real usable range is whatever that version's
world height allows; the declared range is not narrowed per version.

## Layout

- `src/main/java/org/drappula/arcadeBedrockPillars/`
  - `ArcadeBedrockPillars.java`: plugin entrypoint; registers the game,
    listener, and the `/bp` executor and tab completer (declared in
    `plugin.yml`). (The 1.0.0 jar declares this class `final`; the modifier is
    dropped here so MockBukkit can load the plugin in tests. No runtime
    effect.)
  - `BedrockPillarsGame.java`: `Game` implementation (`bedrock-pillars`);
    min/max players come from config, arenas come from the core map pool
    (`createArena` returns null).
  - `BedrockPillarsListener.java`: death to eliminate; last-player-standing
    ends the match with the survivor declared winner; item drops, lava and
    border start with the match; block break/place cancelled during the start
    countdown (movement freeze and spawn cages are core-owned).
  - `BedrockPillarsCommand.java`: `/bp join|leave|chaos`, plus the
    permission-gated `/bp debug win|lose|end` test commands (new in 1.1.0).
  - `ItemDropTask.java`: repeating item-drop task; resolves `items` through
    XSeries `XMaterial`.
  - `PoolLavaTask.java`: rising lava over the pool map's spawn region; every
    placed cell is restored at match end because pool worlds persist.
- `src/main/resources/`: `config.yml` and `plugin.yml` (no `api-version`;
  `depend: [ArcadeCore]`, `commands: bp`).
- `src/test/`: MockBukkit + Mockito suite (56 tests), see below.

## Deviations from the 1.0.0 jar

Fifteen, all deliberate:

1. `ArcadeBedrockPillars` is no longer `final` (see above).
2. `/bp join` checks `joinQueue(...) != JoinResult.SUCCESS`. The 1.0.0 jar was
   built against an older ArcadeAPI whose `joinQueue` returned `boolean`; the
   current API returns the `JoinResult` enum. Behavior is unchanged (any
   non-success still prints "Failed to join the queue.").
3. Elimination ends the match at last-player-standing with the survivor
   declared winner (1.0.1 fix). The 1.0.0 jar ended the match for 2-player
   matches only and never declared winners, so no stats were ever recorded.
4. Op-only `/bp debug win|lose|end` test commands (new in 1.1.0, absent from
   the 1.0.0 jar): `win` declares you winner and ends the match, `lose`
   eliminates you, `end` ends with no winners. Requires the
   `bedrockpillars.debug` permission (ops by default).
5. Arenas build in the core-provided arena world (new in 1.2.0). The
   `arena.world` config key is gone; `center-x/z` and `base-y` still position
   the pillar circle.
6. Players are frozen on their spawn block during the start countdown (new
   in 1.3.0, looking around still works); glass cages open when the match
   starts instead of 1 s after teleport. The 1.0.0 jar freed cages almost
   immediately and never froze movement.
7. Item drops and lava rise start with the match, not the countdown (new in
   1.4.0). During the countdown a bossbar shows honest chunk-load progress
   for the arena region instead.
8. Breaking or placing blocks is cancelled during the start countdown (new
   in 1.4.1), alongside the movement freeze. Once started, the arena is
   fully editable again.
9. A waiting hint shows from queue join (new in 1.5.0). It was a blue
   bossbar, simplified in 1.6.0 (no chunk-load bar anymore). In the
   multi-version build it is an action bar message "Waiting for players" on
   every version, see 14.
10. Static pool arenas (new in 1.6.0): `createArena` returns null, so matches
    run on hand-built maps from `/arcade map` instead of generated pillar
    circles. The builder, map view, lava task, load task, glass cages, world
    border handling, and the `lava-blocks-rise-per-minute` option are gone;
    the core owns countdown spawn cages and movement freeze now.
11. Pool lava and border (new in 1.7.0): lava rises over the pool map's spawn
    bounding box (±3), driven by map options `lava-blocks-rise-per-minute`
    (default 2, 0 disables), `lava-start-y` (default 64) and `border-size`
    (default 0 = leave the border alone). Placed lava and the border are
    restored at match end.
12. Multi-version build (1.8 to 26.3, one jar). The plugin is compiled for
    Java 8 against spigot-api 1.8.8, so Paper-only API is gone: Brigadier
    commands became a plain Bukkit `CommandExecutor` + `TabCompleter`,
    `paper-plugin.yml` became `plugin.yml`, MiniMessage/Adventure became the
    `Messages` helper in ArcadeAPI, and logging uses `java.util.logging`.
13. ArcadeAPI 1.1.0 (was 1.0.0), a breaking change in the shared API:
    `IMatch.broadcast(String, TagResolver...)` is now
    `broadcast(String text, String... placeholders)`, taking alternating
    key/value strings, and paper-api/adventure no longer leak through
    ArcadeAPI. Anything calling the old signature must be updated. This needs
    agreement with the other maintainer (mallu) before merging to `main`.
14. No boss bar on any version: boss bars degrade to an action bar in the
    `Messages` helper, so the waiting hint is an action bar message even on
    servers that have boss bars. Titles and action bars come from XSeries.
15. Items and lava resolve through XSeries `XMaterial`, so `config.yml` keeps
    modern names (`OAK_PLANKS`, `WHITE_WOOL`) and the same file works on old
    and new servers. Lava is `XMaterial.LAVA` (stationary lava on 1.8 to
    1.12). Items the running version does not have are skipped.

## Behavior notes (as recreated)

- `getPlayersRequired()` delegates to `min-players` (default 1/20).
- Arena: a hand-built pool map with at least `max-players` spawns
  (`/arcade map create|addspawn|enable`). No match can start without one;
  the core fails the start with `map-unavailable` and requeues the players.
- Items drop every `item-interval-seconds` (default 5, or the map's own
  `item-interval-seconds` when set) once the match has started, never during
  the countdown. Projectiles (arrow/snowball/egg) come in stacks of 8,
  everything else singly. Unknown material names in `items` are skipped.
- Lava rises starting at `lava-start-y`, at `lava-blocks-rise-per-minute`
  layers per minute, once the match has started, never during the
  countdown. The flooded region is the full `border-size` square around the
  spawn centroid when a border is set, otherwise the spawn bounding box
  (±3). Every placed cell is restored to its prior block at match end,
  since pool worlds persist. The rise stops at the world's max height.
  `border-size` shrinks the world border to the spawn centroid for the
  match (0 = untouched) and restores it after. Prefer a dedicated world per
  map: the border shrink affects everyone in that world, not just players.
- Elimination ends the match when one player is left standing, and the
  survivor is declared winner via `endWithWinners` (so wins/losses are
  recorded). Works for any match size. Kills and survival seconds are
  recorded as custom stats (`kills`, `survival_seconds`).

## Per-version caveats

- 1.8: no boss bar, so the waiting hint is an action bar message (as on every
  version). The default pool includes `SHIELD` and `SCAFFOLDING`, which 1.8
  does not have; they are skipped, so those drops never appear.
- Older versions in general: any `items` entry the server does not know is
  skipped without an error, so the drop pool shrinks instead of failing.
  Check a test match if the pool matters to you.
- `lava-start-y` accepts -64..320 on every version. Worlds before 1.18 start
  at Y 0, so a negative start is meaningless there, and the lava rise stops at
  the world's max height. Pick values inside your version's range.
- Titles and action bars come from XSeries, so their behavior on a given
  version is XSeries', not this plugin's.
- Only boot and command smoke are verified per version (see "Supported
  versions"). Gameplay on each version is not.

## Build & verify

ArcadeAPI resolves from the sibling `../ArcadeCore` build via composite-build
dependency substitution (`org.drappula:ArcadeAPI:1.1.0`), so the addon always
compiles against current API sources. XSeries is shaded into the jar and
relocated to `org.drappula.arcadeBedrockPillars.libs.xseries`. Never run
Gradle in two of the sibling projects at the same time: they share the
included ArcadeCore build directory and clobber each other.

- `./gradlew build`: compiles (Java 8 bytecode), runs tests, builds the jars.
- `./gradlew shadowJar`: `build/libs/ArcadeBedrockPillars-<version>.jar`, the
  jar to install. The plain `jar` task produces a `-thin` jar that lacks
  XSeries.
- `./gradlew test`: unit suite (JUnit 5 + Mockito + MockBukkit, JDK 21).
  56 tests pass. In the test classpath MockBukkit must come before
  `paper-api`.
- Real servers: there is no `runServer` task anymore (the run-paper plugin
  was removed). To boot servers across versions use `../TestServer-matrix`
  (`matrix.sh` boots every version with the current jars; `run-server.sh`
  feeds console commands over stdin), or start a server yourself and copy
  ArcadeCore plus this jar into `plugins/`.
- `./gradlew smokeTest` is still registered, but `scripts/smoke-test.sh` and
  `scripts/prepare-run.sh` were written for the run-paper setup (they expect
  a Paper jar bootstrapped under `run/` and a hardcoded ArcadeCore jar path),
  so they are not part of the verified flow now.
