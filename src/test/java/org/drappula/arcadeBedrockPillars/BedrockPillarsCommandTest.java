package org.drappula.arcadeBedrockPillars;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.drappula.arcadeApi.message.LegacyText;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.queue.JoinResult;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockPillarsCommandTest extends PluginTest {

    private final BedrockPillarsGame game = new BedrockPillarsGame();
    private final BedrockPillarsCommand command = new BedrockPillarsCommand(game);

    private void run(CommandSender sender, String... args) {
        command.onCommand(sender, mock(Command.class), "bp", args);
    }

    private String nextMessage(PlayerMock player) {
        return LegacyText.strip(player.nextMessage());
    }

    private String nextConsoleMessage() {
        return LegacyText.strip(server.getConsoleSender().nextMessage());
    }

    private PlayerMock debugPlayer() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "bedrockpillars.debug", true);
        return player;
    }

    @Test
    void tabCompletionHidesDebugWithoutPermission() {
        PlayerMock plain = server.addPlayer();
        PlayerMock privileged = debugPlayer();

        assertEquals(Arrays.asList("join", "leave", "chaos"),
                command.onTabComplete(plain, mock(Command.class), "bp", new String[]{""}));
        assertTrue(command.onTabComplete(privileged, mock(Command.class), "bp", new String[]{""}).contains("debug"));
        assertEquals(Arrays.asList("win", "lose", "end"),
                command.onTabComplete(privileged, mock(Command.class), "bp", new String[]{"debug", ""}));
    }

    @Test
    void debugRequiresPermission() {
        PlayerMock player = server.addPlayer();

        run(player, "debug", "win");

        assertTrue(nextMessage(player).contains("permission"));
    }

    @Test
    void infoShowsUsage() {
        PlayerMock player = server.addPlayer();

        run(player);

        assertTrue(nextMessage(player).contains("/bp join | /bp leave"));
    }

    @Test
    void joinQueuesPlayer() {
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.SUCCESS);

        run(player, "join");

        verify(queueManager).joinQueue(eq(player), any(Game.class));
        assertTrue(nextMessage(player).contains("Joined the Bedrock Pillars queue."));
    }

    @Test
    void joinFailureExplains() {
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.ALREADY_QUEUED);

        run(player, "join");

        assertTrue(nextMessage(player).contains("Failed to join the queue."));
    }

    @Test
    void joinRejectsConsole() {
        run(server.getConsoleSender(), "join");

        assertTrue(nextConsoleMessage().contains("Only players can join"));
    }

    @Test
    void leaveRemovesFromQueue() {
        PlayerMock player = server.addPlayer();

        run(player, "leave");

        verify(queueManager).leaveQueue(player);
        assertTrue(nextMessage(player).contains("Left the Bedrock Pillars queue."));
    }

    @Test
    void leaveRejectsConsole() {
        run(server.getConsoleSender(), "leave");

        assertTrue(nextConsoleMessage().contains("Only players can leave"));
    }

    /** Spectator in a match of this game with one alive target player. */
    private IMatch chaosMatch(PlayerMock spectator, PlayerMock target) {
        var matchManager = mock(org.drappula.arcadeApi.systems.game.IMatchManager.class);
        when(api.getMatchManager()).thenReturn(matchManager);
        IMatch match = mock(IMatch.class);
        when(match.getGame()).thenReturn(game);
        when(match.isSpectating(spectator)).thenReturn(true);
        IParticipant alive = mock(IParticipant.class);
        when(alive.getPlayer()).thenReturn(target);
        when(match.getAliveParticipants()).thenReturn(List.of(alive));
        when(matchManager.getMatch(spectator)).thenReturn(java.util.Optional.of(match));
        plugin.getConfig().set("items", List.of("COBWEB"));
        return match;
    }

    @Test
    void chaosGivesTargetAnItemAndBroadcastsWithMessagePrefix() {
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        IMatch match = chaosMatch(spectator, target);

        run(spectator, "chaos");

        assertTrue(target.getInventory().contains(org.bukkit.Material.COBWEB));
        // The core drops unprefixed broadcast text for players.
        verify(match).broadcast(org.mockito.ArgumentMatchers.startsWith("message:"),
                eq("spectator"), eq(spectator.getName()), eq("target"), eq(target.getName()));
    }

    @Test
    void chaosHonoursCooldown() {
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        IMatch match = chaosMatch(spectator, target);

        run(spectator, "chaos");
        run(spectator, "chaos");

        verify(match, org.mockito.Mockito.times(1)).broadcast(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void chaosRejectsNonSpectators() {
        PlayerMock player = server.addPlayer();
        var matchManager = mock(org.drappula.arcadeApi.systems.game.IMatchManager.class);
        when(api.getMatchManager()).thenReturn(matchManager);
        when(matchManager.getMatch(player)).thenReturn(java.util.Optional.empty());

        run(player, "chaos");

        assertTrue(nextMessage(player).contains("must be spectating"));
    }

    @Test
    void debugWinEndsMatchWithWinner() {
        PlayerMock player = debugPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        run(player, "debug", "win");

        verify(match).endWithWinners(java.util.Collections.singletonList(participant));
        assertTrue(nextMessage(player).contains("winner"));
    }

    @Test
    void debugLoseEliminatesSelf() {
        PlayerMock player = debugPlayer();
        IParticipant participant = mock(IParticipant.class);
        when(participant.getPlayer()).thenReturn(player);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        run(player, "debug", "lose");

        verify(participant).eliminate();
        assertTrue(nextMessage(player).contains("eliminated"));
    }

    @Test
    void debugEndEndsMatchWithoutWinners() {
        PlayerMock player = debugPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        run(player, "debug", "end");

        verify(match).end();
        verify(match, never()).setWinnerParticipants(any());
        assertTrue(nextMessage(player).contains("ended"));
    }

    @Test
    void debugOutsideMatchExplains() {
        PlayerMock player = debugPlayer();
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(null);

        run(player, "debug", "win");

        assertTrue(nextMessage(player).contains("not in a Bedrock Pillars match"));
    }

    @Test
    void debugRejectsConsole() {
        CommandSender console = server.getConsoleSender();
        console.addAttachment(plugin, "bedrockpillars.debug", true);
        run(console, "debug", "win");

        assertTrue(nextConsoleMessage().contains("Only players can use debug commands"));
    }
}
