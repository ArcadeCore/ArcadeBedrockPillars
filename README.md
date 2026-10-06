# ArcadeBedrockPillars

Bedrock Pillars minigame addon for [ArcadeCore](../ArcadeCore). Matches run
on hand-built pool maps; periodic item drops and a map-configured rising
lava level force the fight until eliminations decide the match.

This project is a source recreation of `ArcadeBedrockPillars-1.0.0.jar`
(CFR decompile + bytecode verification). Recompiling reproduces the original
jar entry-for-entry: same 9 classes, same `config.yml` / `paper-plugin.yml`.
(1.6.0 diverges: static pool arenas, 5 classes.)

## Layout

- `src/main/java/org/drappula/arcadeBedrockPillars/`
  - `ArcadeBedrockPillars.java` — plugin entrypoint; registers the game,
    listener, and `/bp` command. (The 1.0.0 jar declares this class `final`;
    the modifier is dropped here so MockBukkit can load the plugin in tests.
    No runtime effect.)
  - `BedrockPillarsGame.java` — `Game` implementation (`bedrock-pillars`);
    min/max players come from config, arenas come from the core map pool
    (`createArena` returns null).
  - `BedrockPillarsListener.java` — death → eliminate; last-player-standing
    ends the match with the survivor declared winner; item drops start/stop
    with the match; block break/place cancelled during the start countdown
    (movement freeze and spawn cages are core-owned now).
  - `BedrockPillarsCommand.java` — `/bp`, `/bp join`, `/bp leave`, plus
    op-only `/bp debug win|lose|end` test commands (new in 1.1.0).
  - `ItemDropTask.java` — repeating item-drop task.
  - `PoolLavaTask.java` — rising lava over the pool map's spawn region; every
    placed cell is restored at match end because pool worlds persist.
- `src/main/resources/` — `config.yml`, `paper-plugin.yml` (byte-identical to
  the 1.0.0 jar).
- `src/test/` — MockBukkit + Mockito suite (43 tests), see below.

## Deviations from the 1.0.0 jar

Nine, all deliberate:

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
9. A blue waiting bossbar shows from queue join (new in 1.5.0, simplified in
   1.6.0: no chunk-load bar anymore, the waiting bar is swept at match
   start so it can never linger into gameplay).
10. Static pool arenas (new in 1.6.0): `createArena` returns null, so matches
    run on hand-built maps from `/arcade map` instead of generated pillar
    circles. The builder, map view, lava task, load task, glass cages, world
    border handling, and the `lava-blocks-rise-per-minute` option are gone;
    the core owns countdown spawn cages and movement freeze now.
11. Pool lava and border (new in 1.7.0): lava rises over the pool map's spawn
    bounding box (±3), driven by three map options —
    `lava-blocks-rise-per-minute` (default 2, 0 disables), `lava-start-y`
    (default 64), `border-size` (default 0 = leave the border alone).
    Placed lava and the border are restored at match end.

## Behavior notes (as recreated)

- `getPlayersRequired()` delegates to `min-players` (default 1/20).
- Arena: a hand-built pool map with at least `max-players` spawns
  (`/arcade map create|addspawn|enable`). No match can start without one —
  the core fails the start with `map-unavailable` and requeues the players.
- Items drop every `item-interval-seconds` (default 5) once the match has
  started — never during the countdown; projectiles
  (arrow/snowball/egg) in stacks of 8, everything else singly. Unknown
  material names in `items` are skipped.
- Lava rises starting at `lava-start-y`, at `lava-blocks-rise-per-minute`
  layers per minute, once the match has started — never during the
  countdown. The flooded region is the full `border-size` square around the
  spawn centroid when a border is set, otherwise the spawn bounding box
  (±3). Every placed cell is restored to its prior block at match end,
  since pool worlds persist.
  `border-size` shrinks the world border to the spawn centroid for the
  match (0 = untouched) and restores it after. Prefer a dedicated world per
  map: the border shrink affects everyone in that world, not just players.
- Elimination ends the match when one player is left standing, and the
  survivor is declared winner via `endWithWinners` (so wins/losses are
  recorded). Works for any match size.

## Build & verify

ArcadeAPI resolves from the sibling `../ArcadeCore` build via composite-build
dependency substitution, so the addon always compiles against current API
sources.

- `./gradlew build` — compiles, runs tests, builds the thin jar.
- `./gradlew test` — unit suite (JUnit 5 + Mockito + MockBukkit).
- `./gradlew smokeTest` — boots a real Paper 1.21.11 server in tmux with
  ArcadeCore + this addon, asserts both enable cleanly, runs `bp` from
  console, stops (~5 min first run for the Paper download). The server is
  capped at 1G heap and the script refuses to boot with < 1G free, so it
  can't OOM the machine; bootstrap Paper once with `./gradlew runServer`
  (Ctrl-C after `Done`) before the first smoke run.
- `./gradlew runServer` — interactive local server (run
  `scripts/prepare-run.sh` first to install both plugins into `run/plugins`).
