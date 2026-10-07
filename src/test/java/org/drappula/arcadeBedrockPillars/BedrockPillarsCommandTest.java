package org.drappula.arcadeBedrockPillars;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.queue.JoinResult;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockPillarsCommandTest extends PluginTest {

    private final BedrockPillarsGame game = new BedrockPillarsGame();

    private CommandContext<CommandSourceStack> contextWithSender(Object sender) {
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(stack.getSender()).thenReturn((org.bukkit.command.CommandSender) sender);
        @SuppressWarnings("unchecked")
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(stack);
        return context;
    }

    private int invoke(String name, CommandContext<CommandSourceStack> context, boolean withGame) throws Exception {
        Method method;
        Object result;
        if (withGame) {
            method = BedrockPillarsCommand.class.getDeclaredMethod(name, CommandContext.class, BedrockPillarsGame.class);
            method.setAccessible(true);
            result = method.invoke(null, context, game);
        } else {
            method = BedrockPillarsCommand.class.getDeclaredMethod(name, CommandContext.class);
            method.setAccessible(true);
            result = method.invoke(null, context);
        }
        return (int) result;
    }

    private String nextMessage(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }

    private String nextConsoleMessage() {
        return PlainTextComponentSerializer.plainText()
                .serialize(server.getConsoleSender().nextComponentMessage());
    }

    @Test
    void commandTreeShape() {
        LiteralCommandNode<CommandSourceStack> node = BedrockPillarsCommand.get(game);

        assertEquals("bp", node.getName());
        Set<String> children = node.getChildren().stream()
                .map(child -> child.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("join", "leave", "chaos", "debug"), children);
    }

    @Test
    void infoShowsUsage() throws Exception {
        PlayerMock player = server.addPlayer();

        invoke("info", contextWithSender(player), false);

        assertTrue(nextMessage(player).contains("/bp join | /bp leave"));
    }

    @Test
    void joinQueuesPlayer() throws Exception {
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.SUCCESS);

        invoke("join", contextWithSender(player), true);

        verify(queueManager).joinQueue(eq(player), any(Game.class));
        assertTrue(nextMessage(player).contains("Joined the Bedrock Pillars queue."));
    }

    @Test
    void joinFailureExplains() throws Exception {
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.ALREADY_QUEUED);

        invoke("join", contextWithSender(player), true);

        assertTrue(nextMessage(player).contains("Failed to join the queue."));
    }

    @Test
    void joinRejectsConsole() throws Exception {
        invoke("join", contextWithSender(server.getConsoleSender()), true);

        assertTrue(nextConsoleMessage().contains("Only players can join"));
    }

    @Test
    void leaveRemovesFromQueue() throws Exception {
        PlayerMock player = server.addPlayer();

        invoke("leave", contextWithSender(player), false);

        verify(queueManager).leaveQueue(player);
        assertTrue(nextMessage(player).contains("Left the Bedrock Pillars queue."));
    }

    @Test
    void leaveRejectsConsole() throws Exception {
        invoke("leave", contextWithSender(server.getConsoleSender()), false);

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
    void chaosGivesTargetAnItemAndBroadcastsWithMessagePrefix() throws Exception {
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        IMatch match = chaosMatch(spectator, target);

        invoke("chaos", contextWithSender(spectator), true);

        assertTrue(target.getInventory().contains(org.bukkit.Material.COBWEB));
        // The core drops unprefixed broadcast text for players.
        verify(match).broadcast(org.mockito.ArgumentMatchers.startsWith("message:"));
    }

    @Test
    void chaosHonoursCooldown() throws Exception {
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        IMatch match = chaosMatch(spectator, target);

        invoke("chaos", contextWithSender(spectator), true);
        invoke("chaos", contextWithSender(spectator), true);

        verify(match, org.mockito.Mockito.times(1)).broadcast(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void chaosRejectsNonSpectators() throws Exception {
        PlayerMock player = server.addPlayer();
        var matchManager = mock(org.drappula.arcadeApi.systems.game.IMatchManager.class);
        when(api.getMatchManager()).thenReturn(matchManager);
        when(matchManager.getMatch(player)).thenReturn(java.util.Optional.empty());

        invoke("chaos", contextWithSender(player), true);

        assertTrue(nextMessage(player).contains("must be spectating"));
    }

    @Test
    void debugWinEndsMatchWithWinner() throws Exception {
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugWin", contextWithSender(player), true);

        verify(match).endWithWinners(List.of(participant));
        assertTrue(nextMessage(player).contains("winner"));
    }

    @Test
    void debugLoseEliminatesSelf() throws Exception {
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        when(participant.getPlayer()).thenReturn(player);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugLose", contextWithSender(player), true);

        verify(participant).eliminate();
        assertTrue(nextMessage(player).contains("eliminated"));
    }

    @Test
    void debugEndEndsMatchWithoutWinners() throws Exception {
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugEnd", contextWithSender(player), true);

        verify(match).end();
        verify(match, never()).setWinnerParticipants(any());
        assertTrue(nextMessage(player).contains("ended"));
    }

    @Test
    void debugOutsideMatchExplains() throws Exception {
        PlayerMock player = server.addPlayer();
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(null);

        invoke("debugWin", contextWithSender(player), true);

        assertTrue(nextMessage(player).contains("not in a Bedrock Pillars match"));
    }

    @Test
    void debugRejectsConsole() throws Exception {
        invoke("debugWin", contextWithSender(server.getConsoleSender()), true);

        assertTrue(nextConsoleMessage().contains("Only players can use debug commands"));
    }
}
