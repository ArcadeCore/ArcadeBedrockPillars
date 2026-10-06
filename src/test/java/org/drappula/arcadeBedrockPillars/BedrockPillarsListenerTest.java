package org.drappula.arcadeBedrockPillars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.drappula.arcadeApi.events.MatchEndEvent;
import org.drappula.arcadeApi.events.MatchStartEvent;
import org.drappula.arcadeApi.events.QueueEnterEvent;
import org.drappula.arcadeApi.events.QueueLeaveEvent;
import org.drappula.arcadeApi.systems.queue.QueueLeaveReason;
import org.drappula.arcadeApi.events.MatchStateChangeEvent;
import org.drappula.arcadeApi.events.ParticipantEliminateEvent;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.game.MatchState;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockPillarsListenerTest extends PluginTest {

    private final BedrockPillarsGame game = new BedrockPillarsGame();

    private PlayerDeathEvent deathOf(PlayerMock player) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        return new PlayerDeathEvent(player, source, List.of(), 0, "died");
    }

    private IParticipant participant(PlayerMock player, IMatch match, boolean eliminated) {
        IParticipant participant = mock(IParticipant.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(participant.isEliminated()).thenReturn(eliminated);
        return participant;
    }

    private IMatch matchOf(Game game, List<IParticipant> participants) {
        IMatch match = mock(IMatch.class);
        when(match.getGame()).thenReturn(game);
        when(match.getParticipants()).thenReturn(participants);
        return match;
    }

    private static org.drappula.arcadeApi.systems.map.IArcadeMap poolMap() {
        org.drappula.arcadeApi.systems.map.IArcadeMap map =
                mock(org.drappula.arcadeApi.systems.map.IArcadeMap.class);
        when(map.getId()).thenReturn("void");
        return map;
    }

    private static int totalItems(PlayerMock player) {
        int total = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null) total += item.getAmount();
        }
        return total;
    }

    @Test
    void deathEliminatesParticipant() {
        PlayerMock player = server.addPlayer();
        IMatch match = matchOf(game, List.of());
        IParticipant participant = participant(player, match, false);
        when(api.getParticipant(any(Game.class), any(PlayerMock.class))).thenReturn(participant);

        server.getPluginManager().callEvent(deathOf(player));

        verify(participant).eliminate();
    }

    @Test
    void deathIgnoresAlreadyEliminatedAndStrangers() {
        PlayerMock out = server.addPlayer();
        PlayerMock stranger = server.addPlayer();
        IMatch match = matchOf(game, List.of());
        IParticipant eliminated = participant(out, match, true);
        when(api.getParticipant(any(Game.class), any(PlayerMock.class)))
                .thenAnswer(invocation -> invocation.getArgument(1).equals(out) ? eliminated : null);

        server.getPluginManager().callEvent(deathOf(out));
        server.getPluginManager().callEvent(deathOf(stranger));

        verify(eliminated, never()).eliminate();
    }

    @Test
    void queueEnterShowsWaitingBar() {
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(true);

        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));

        assertFalse(player.getBossBars().isEmpty());
    }

    @Test
    void queueEnterDeniedHidesBar() {
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(false);

        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));
        server.getScheduler().performTicks(1);

        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void queueLeaveHidesBar() {
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(true);
        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));
        assertFalse(player.getBossBars().isEmpty());

        server.getPluginManager().callEvent(
                new QueueLeaveEvent(player, game, QueueLeaveReason.LEAVE));

        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void queueEventsIgnoreOtherGames() {
        Game other = mock(Game.class);
        when(other.getId()).thenReturn("other-game");
        PlayerMock player = server.addPlayer();

        server.getPluginManager().callEvent(new QueueEnterEvent(player, other));
        server.getScheduler().performTicks(1);

        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void startedTransitionClearsWaitingBars() {
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(true);
        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));
        server.getScheduler().performTicks(1);
        assertFalse(player.getBossBars().isEmpty());
        IMatch match = matchOf(game, List.of(participant(player, null, false)));

        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));

        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void fullQueueToStartLeavesNoWaitingBar() {
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(true);
        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));
        server.getScheduler().performTicks(1);
        assertFalse(player.getBossBars().isEmpty());

        // Production order: queue-leave (match start) fires before MatchStartEvent.
        IMatch match = matchOf(game, List.of(participant(player, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap();
        when(match.getMap()).thenReturn(pool);
        server.getPluginManager().callEvent(
                new QueueLeaveEvent(player, game, QueueLeaveReason.MATCH_START));
        assertTrue(player.getBossBars().isEmpty());

        server.getPluginManager().callEvent(new MatchStartEvent(match));
        server.getScheduler().performTicks(20);

        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));
        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void eliminationEndsTwoPlayerMatchWithWinner() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        IMatch match = matchOf(game, List.of());
        doCallRealMethod().when(match).endWithWinners(any());
        IParticipant victim = participant(first, match, false);
        IParticipant survivor = participant(second, match, false);
        when(match.getParticipants()).thenReturn(List.of(victim, survivor));
        when(match.getAliveCount()).thenReturn(1);
        when(match.getAliveParticipants()).thenReturn(List.of(survivor));

        server.getPluginManager().callEvent(new ParticipantEliminateEvent(victim));
        server.getScheduler().performTicks(1);

        verify(match).setWinnerParticipants(List.of(survivor));
        verify(match).end();
    }

    @Test
    void soloMatchEndsWithNoWinners() {
        IMatch match = matchOf(game, List.of());
        doCallRealMethod().when(match).endWithWinners(any());
        IParticipant victim = participant(server.addPlayer(), match, false);
        when(match.getParticipants()).thenReturn(List.of(victim));
        when(match.getAliveCount()).thenReturn(0);
        when(match.getAliveParticipants()).thenReturn(List.of());

        server.getPluginManager().callEvent(new ParticipantEliminateEvent(victim));
        server.getScheduler().performTicks(1);

        verify(match, never()).setWinnerParticipants(any());
        verify(match).end();
    }

    @Test
    void threePlayerMatchEndsOnLastElimination() {
        IMatch match = matchOf(game, List.of());
        doCallRealMethod().when(match).endWithWinners(any());
        IParticipant first = participant(server.addPlayer(), match, false);
        IParticipant second = participant(server.addPlayer(), match, false);
        IParticipant survivor = participant(server.addPlayer(), match, false);
        when(match.getParticipants()).thenReturn(List.of(first, second, survivor));
        when(match.getAliveCount()).thenReturn(2);
        when(match.getAliveParticipants()).thenReturn(List.of(second, survivor));

        server.getPluginManager().callEvent(new ParticipantEliminateEvent(first));
        server.getScheduler().performTicks(1);

        verify(match, never()).end();

        when(match.getAliveCount()).thenReturn(1);
        when(match.getAliveParticipants()).thenReturn(List.of(survivor));
        server.getPluginManager().callEvent(new ParticipantEliminateEvent(second));
        server.getScheduler().performTicks(1);

        verify(match).setWinnerParticipants(List.of(survivor));
        verify(match).end();
    }

    @Test
    void eliminationIgnoresOtherGames() {
        Game other = mock(Game.class);
        when(other.getId()).thenReturn("other-game");
        IMatch match = matchOf(other, List.of());
        IParticipant victim = participant(server.addPlayer(), match, false);
        when(match.getParticipants()).thenReturn(List.of(victim, mock(IParticipant.class)));

        server.getPluginManager().callEvent(new ParticipantEliminateEvent(victim));
        server.getScheduler().performTicks(5);

        verify(match, never()).end();
    }

    @Test
    void itemsStartOnPoolMapWithoutArena() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        plugin.getConfig().set("item-interval-seconds", 5);
        IMatch match = matchOf(game, List.of(
                participant(first, null, false), participant(second, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap();
        when(match.getMap()).thenReturn(pool);

        server.getPluginManager().callEvent(new MatchStartEvent(match));

        // No loading bar for pool maps.
        assertTrue(first.getBossBars().isEmpty());

        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));

        // Item drops flow every interval from the start.
        server.getScheduler().performTicks(5 * 20);
        assertNotNull(first.getInventory().getItem(0));
    }

    @Test
    void matchStartIgnoresForeignMatches() {
        Game other = mock(Game.class);
        when(other.getId()).thenReturn("other-game");
        PlayerMock player = server.addPlayer();
        IMatch match = matchOf(other, List.of(participant(player, null, false)));

        server.getPluginManager().callEvent(new MatchStartEvent(match));
        server.getScheduler().performTicks(600);

        verify(match, never()).getMap();
    }

    @Test
    void matchEndCancelsItemTask() {        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        plugin.getConfig().set("item-interval-seconds", 5);
        IMatch match = matchOf(game, List.of(
                participant(first, null, false), participant(second, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap();
        when(match.getMap()).thenReturn(pool);
        server.getPluginManager().callEvent(new MatchStartEvent(match));
        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));
        server.getScheduler().performTicks(5 * 20);
        assertNotNull(first.getInventory().getItem(0));
        int total = totalItems(first);

        server.getPluginManager().callEvent(new MatchEndEvent(match));

        // Cancelled task drops nothing more over the rest of the match.
        server.getScheduler().performTicks(10 * 20);
        assertEquals(total, totalItems(first));
    }

    private org.drappula.arcadeApi.systems.map.IArcadeMap poolMap(java.util.Map<String, String> overrides) {
        java.util.List<Location> spawns = java.util.List.of(
                new Location(arenaWorld, 0, 80, 0),
                new Location(arenaWorld, 10, 90, 5));
        return new org.drappula.arcadeApi.systems.map.IArcadeMap() {
            @Override
            public String getId() {
                return "void";
            }

            @Override
            public String getGameId() {
                return "bedrock-pillars";
            }

            @Override
            public String getDisplayName() {
                return "Void";
            }

            @Override
            public String getWorldName() {
                return "arena-world";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public boolean isInUse() {
                return false;
            }

            @Override
            public java.util.List<Location> getSpawnPoints() {
                return spawns;
            }

            @Override
            public World getWorld() {
                return arenaWorld;
            }

            @Override
            public java.util.Map<String, String> getConfigOverrides() {
                return overrides;
            }
        };
    }

    @Test
    void lavaAndBorderStartWithMatch() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        IMatch match = matchOf(game, List.of(
                participant(first, null, false), participant(second, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap(java.util.Map.of(
                "lava-blocks-rise-per-minute", "60",
                "lava-start-y", "70",
                "border-size", "40"));
        when(match.getMap()).thenReturn(pool);

        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));

        org.bukkit.WorldBorder border = arenaWorld.getWorldBorder();
        assertEquals(5.0, border.getCenter().getX());
        assertEquals(2.5, border.getCenter().getZ());
        assertEquals(40.0, border.getSize());
        server.getScheduler().performTicks(20);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(0, 70, 0).getType());
    }

    @Test
    void matchEndRestoresBorderAndLava() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        IMatch match = matchOf(game, List.of(
                participant(first, null, false), participant(second, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap(java.util.Map.of(
                "lava-blocks-rise-per-minute", "60",
                "lava-start-y", "70",
                "border-size", "40"));
        when(match.getMap()).thenReturn(pool);
        org.bukkit.WorldBorder border = arenaWorld.getWorldBorder();
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        double size = border.getSize();
        arenaWorld.getBlockAt(2, 71, 2).setType(Material.STONE);
        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));
        server.getScheduler().performTicks(40);

        server.getPluginManager().callEvent(new MatchEndEvent(match));

        assertEquals(centerX, border.getCenter().getX());
        assertEquals(centerZ, border.getCenter().getZ());
        assertEquals(size, border.getSize());
        assertEquals(Material.AIR, arenaWorld.getBlockAt(2, 70, 2).getType());
        assertEquals(Material.STONE, arenaWorld.getBlockAt(2, 71, 2).getType());
    }

    @Test
    void zeroBorderLeavesBorderAlone() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        IMatch match = matchOf(game, List.of(
                participant(first, null, false), participant(second, null, false)));
        org.drappula.arcadeApi.systems.map.IArcadeMap pool = poolMap(java.util.Map.of(
                "lava-blocks-rise-per-minute", "60",
                "lava-start-y", "70",
                "border-size", "0"));
        when(match.getMap()).thenReturn(pool);
        org.bukkit.WorldBorder border = arenaWorld.getWorldBorder();
        double centerX = border.getCenter().getX();
        double size = border.getSize();

        server.getPluginManager().callEvent(
                new MatchStateChangeEvent(match, MatchState.STARTING, MatchState.STARTED));

        assertEquals(centerX, border.getCenter().getX());
        assertEquals(size, border.getSize());
        server.getScheduler().performTicks(20);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(0, 70, 0).getType());
    }

    private void inStartingMatch(PlayerMock player, IMatch match) {
        IParticipant participant = participant(player, match, false);
        when(api.getParticipant(any(Game.class), any(PlayerMock.class))).thenReturn(participant);
        when(match.getGame()).thenReturn(game);
        when(match.getState()).thenReturn(MatchState.STARTING);
    }

    @Test
    void breakAndPlaceCancelledDuringCountdown() {
        PlayerMock player = server.addPlayer();
        IMatch match = mock(IMatch.class);
        inStartingMatch(player, match);
        org.bukkit.block.Block block = arenaWorld.getBlockAt(0, 70, 0);

        org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(breakEvent);
        assertTrue(breakEvent.isCancelled());

        org.bukkit.event.block.BlockPlaceEvent placeEvent =
                new org.bukkit.event.block.BlockPlaceEvent(block,
                        block.getState(), block,
                        new org.bukkit.inventory.ItemStack(org.bukkit.Material.GLASS),
                        player, true);
        server.getPluginManager().callEvent(placeEvent);
        assertTrue(placeEvent.isCancelled());
    }

    @Test
    void breakAllowedOnceStarted() {
        PlayerMock player = server.addPlayer();
        IMatch match = mock(IMatch.class);
        inStartingMatch(player, match);
        when(match.getState()).thenReturn(MatchState.STARTED);
        org.bukkit.block.Block block = arenaWorld.getBlockAt(0, 70, 0);

        org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(breakEvent);

        assertFalse(breakEvent.isCancelled());
    }

    @Test
    void breakIgnoredForNonParticipants() {
        PlayerMock stranger = server.addPlayer();
        when(api.getParticipant(any(Game.class), any(PlayerMock.class))).thenReturn(null);

        org.bukkit.block.Block block = arenaWorld.getBlockAt(0, 70, 0);
        org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, stranger);
        server.getPluginManager().callEvent(breakEvent);

        assertFalse(breakEvent.isCancelled());
    }
}
