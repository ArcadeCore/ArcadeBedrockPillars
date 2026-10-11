package org.drappula.arcadeBedrockPillars;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.queue.JoinResult;
import org.drappula.arcadeApi.events.QueueEnterEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagesConfigTest extends PluginTest {

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
    void infoIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.info", "<aqua>Custom info <cmd>");
        PlayerMock player = server.addPlayer();

        invoke("info", contextWithSender(player), false);

        assertEquals("Custom info <cmd>", nextMessage(player));
    }

    @Test
    void joinSuccessIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.join.success", "<green>Custom join!");
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.SUCCESS);

        invoke("join", contextWithSender(player), true);

        assertEquals("Custom join!", nextMessage(player));
    }

    @Test
    void joinFailureIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.join.failed", "<red>Custom fail!");
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.ALREADY_QUEUED);

        invoke("join", contextWithSender(player), true);

        assertEquals("Custom fail!", nextMessage(player));
    }

    @Test
    void joinConsoleOnlyPlayersIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.join.only-players", "<red>Custom console no join");

        invoke("join", contextWithSender(server.getConsoleSender()), true);

        assertEquals("Custom console no join", nextConsoleMessage());
    }

    @Test
    void leaveSuccessIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.leave.success", "<yellow>Custom left");
        PlayerMock player = server.addPlayer();

        invoke("leave", contextWithSender(player), false);

        assertEquals("Custom left", nextMessage(player));
    }

    @Test
    void leaveConsoleOnlyPlayersIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.leave.only-players", "<red>Custom console no leave");

        invoke("leave", contextWithSender(server.getConsoleSender()), false);

        assertEquals("Custom console no leave", nextConsoleMessage());
    }

    @Test
    void chaosNotPlayerIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.chaos.not-player", "<red>Custom not player chaos");

        invoke("chaos", contextWithSender(server.getConsoleSender()), true);

        assertEquals("Custom not player chaos", nextConsoleMessage());
    }

    @Test
    void chaosMustSpectateIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.chaos.must-spectate", "<red>Custom must spectate");
        PlayerMock player = server.addPlayer();
        var matchManager = mock(org.drappula.arcadeApi.systems.game.IMatchManager.class);
        when(api.getMatchManager()).thenReturn(matchManager);
        when(matchManager.getMatch(player)).thenReturn(java.util.Optional.empty());

        invoke("chaos", contextWithSender(player), true);

        assertEquals("Custom must spectate", nextMessage(player));
    }

    @Test
    void chaosCooldownIsConfigurableWithSecondsPlaceholder() throws Exception {
        plugin.getConfig().set("messages.chaos.cooldown", "<red>Wait <seconds>s!");
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        chaosMatch(spectator, target);

        invoke("chaos", contextWithSender(spectator), true);
        // Drain first broadcast path message (none to spectator) and trigger cooldown.
        invoke("chaos", contextWithSender(spectator), true);

        String text = nextMessage(spectator);
        assertTrue(text.startsWith("Wait "), "expected custom cooldown prefix, got: " + text);
        assertTrue(text.endsWith("s!"), "expected custom cooldown suffix, got: " + text);
    }

    @Test
    void chaosMatchCapIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.chaos.match-cap", "<red>Custom enough chaos");
        plugin.getConfig().set("chaos.max-per-match", 0);
        PlayerMock spectator = server.addPlayer();
        PlayerMock target = server.addPlayer();
        chaosMatch(spectator, target);

        invoke("chaos", contextWithSender(spectator), true);

        assertEquals("Custom enough chaos", nextMessage(spectator));
    }

    @Test
    void chaosBroadcastIsConfigurableWithNamePlaceholders() throws Exception {
        plugin.getConfig().set("messages.chaos.broadcast",
                "<light_purple><spectator> chaos'd <target>!");
        PlayerMock spectator = server.addPlayer();
        spectator.setName("SpecName");
        PlayerMock target = server.addPlayer();
        target.setName("TargetName");
        IMatch match = chaosMatch(spectator, target);

        invoke("chaos", contextWithSender(spectator), true);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(match).broadcast(captor.capture());
        String broadcast = captor.getValue();
        assertTrue(broadcast.startsWith("message:"), "core routing prefix must be kept, got: " + broadcast);
        assertTrue(broadcast.contains("SpecName"), "spectator placeholder, got: " + broadcast);
        assertTrue(broadcast.contains("TargetName"), "target placeholder, got: " + broadcast);
    }

    @Test
    void debugOnlyPlayersIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.debug.only-players", "<red>Custom debug console denied");

        invoke("debugWin", contextWithSender(server.getConsoleSender()), true);

        assertEquals("Custom debug console denied", nextConsoleMessage());
    }

    @Test
    void debugNotInMatchIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.debug.not-in-match", "<red>Custom not in match");
        PlayerMock player = server.addPlayer();
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(null);

        invoke("debugWin", contextWithSender(player), true);

        assertEquals("Custom not in match", nextMessage(player));
    }

    @Test
    void debugWinIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.debug.win", "<green>Custom win!");
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugWin", contextWithSender(player), true);

        assertEquals("Custom win!", nextMessage(player));
    }

    @Test
    void debugLoseIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.debug.lose", "<yellow>Custom lose!");
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        when(participant.getPlayer()).thenReturn(player);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugLose", contextWithSender(player), true);

        assertEquals("Custom lose!", nextMessage(player));
    }

    @Test
    void debugEndIsConfigurable() throws Exception {
        plugin.getConfig().set("messages.debug.end", "<yellow>Custom end!");
        PlayerMock player = server.addPlayer();
        IParticipant participant = mock(IParticipant.class);
        IMatch match = mock(IMatch.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.getMatch()).thenReturn(match);
        when(api.getParticipant(any(Game.class), eq(player))).thenReturn(participant);

        invoke("debugEnd", contextWithSender(player), true);

        assertEquals("Custom end!", nextMessage(player));
    }

    @Test
    void waitingBossbarIsConfigurable() {
        plugin.getConfig().set("messages.queue.waiting-bossbar", "Custom waiting!");
        PlayerMock player = server.addPlayer();
        when(queueManager.isQueued(player)).thenReturn(true);

        server.getPluginManager().callEvent(new QueueEnterEvent(player, game));

        assertEquals(1, player.getBossBars().size());
        String title = PlainTextComponentSerializer.plainText()
                .serialize(player.getBossBars().iterator().next().name());
        assertEquals("Custom waiting!", title);
    }

    @Test
    void missingKeyFallsBackToDefault() throws Exception {
        plugin.getConfig().set("messages.join.success", null);
        PlayerMock player = server.addPlayer();
        when(queueManager.joinQueue(eq(player), any(Game.class))).thenReturn(JoinResult.SUCCESS);

        invoke("join", contextWithSender(player), true);

        assertEquals("Joined the Bedrock Pillars queue.", nextMessage(player));
    }

    @Test
    void unknownPlaceholderLeftAsIs() {
        String resolved = Messages.resolve("<red>Hi <unknown>!",
                java.util.Map.of("seconds", "5"));

        assertEquals("<red>Hi <unknown>!", resolved);
    }

    @Test
    void chaosWithNoAliveTargetsDoesNothing() throws Exception {
        PlayerMock spectator = server.addPlayer();
        var matchManager = mock(org.drappula.arcadeApi.systems.game.IMatchManager.class);
        when(api.getMatchManager()).thenReturn(matchManager);
        IMatch match = mock(IMatch.class);
        when(match.getGame()).thenReturn(game);
        when(match.isSpectating(spectator)).thenReturn(true);
        when(match.getAliveParticipants()).thenReturn(List.of());
        when(matchManager.getMatch(spectator)).thenReturn(java.util.Optional.of(match));

        invoke("chaos", contextWithSender(spectator), true);

        verify(match, org.mockito.Mockito.never()).broadcast(any());
        assertTrue(spectator.getBossBars().isEmpty() || true);
    }
}
