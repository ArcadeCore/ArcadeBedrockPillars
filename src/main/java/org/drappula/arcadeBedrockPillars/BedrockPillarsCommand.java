package org.drappula.arcadeBedrockPillars;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.queue.JoinResult;

import java.util.List;

public class BedrockPillarsCommand {
    public static LiteralCommandNode<CommandSourceStack> get(BedrockPillarsGame game) {
        return ((LiteralArgumentBuilder<CommandSourceStack>) ((LiteralArgumentBuilder<CommandSourceStack>) ((LiteralArgumentBuilder<CommandSourceStack>) ((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("bp")
                .executes(BedrockPillarsCommand::info))
                .then(Commands.literal("join").executes(ctx -> join(ctx, game))))
                .then(Commands.literal("leave").executes(BedrockPillarsCommand::leave)))
                .then(Commands.literal("chaos").executes(ctx -> chaos(ctx, game)))
                .then(Commands.literal("debug")
                        .requires(source -> source.getSender().hasPermission("bedrockpillars.debug"))
                        .then(Commands.literal("win").executes(ctx -> debugWin(ctx, game)))
                        .then(Commands.literal("lose").executes(ctx -> debugLose(ctx, game)))
                        .then(Commands.literal("end").executes(ctx -> debugEnd(ctx, game)))))
                .build();
    }

    /** Spectators drop a random item on a random surviving player. Limited by cooldown and per-match cap. */
    private static int chaos(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        if (!(ctx.getSource().getSender() instanceof Player spectator)) {
            Messages.send(ctx.getSource().getSender(), Messages.CHAOS_NOT_PLAYER, Messages.CHAOS_NOT_PLAYER_DEFAULT);
            return 1;
        }
        var match = ArcadeAPIProvider.get().getMatchManager().getMatch(spectator).orElse(null);
        if (match == null || !match.getGame().getId().equals(game.getId()) || !match.isSpectating(spectator)) {
            Messages.send(spectator, Messages.CHAOS_MUST_SPECTATE, Messages.CHAOS_MUST_SPECTATE_DEFAULT);
            return 1;
        }
        var cfg = ArcadeBedrockPillars.get().getConfig();
        long cooldownMs = cfg.getLong("chaos.cooldown-seconds", 20) * 1000L;
        int cap = cfg.getInt("chaos.max-per-match", 10);
        long now = System.currentTimeMillis();
        Long last = game.chaosLast.get(spectator.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long seconds = (cooldownMs - (now - last)) / 1000 + 1;
            Messages.send(spectator, Messages.CHAOS_COOLDOWN, Messages.CHAOS_COOLDOWN_DEFAULT,
                    java.util.Map.of("seconds", String.valueOf(seconds)));
            return 1;
        }
        if (game.chaosUses.getOrDefault(match, 0) >= cap) {
            Messages.send(spectator, Messages.CHAOS_MATCH_CAP, Messages.CHAOS_MATCH_CAP_DEFAULT);
            return 1;
        }
        List<IParticipant> alive = match.getAliveParticipants();
        if (alive.isEmpty()) {
            return 1;
        }
        IParticipant target = alive.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(alive.size()));
        if (!new ItemDropTask(match).give(target.getPlayer())) {
            return 1;
        }
        game.chaosLast.put(spectator.getUniqueId(), now);
        game.chaosUses.merge(match, 1, Integer::sum);
        // Core routes by prefix; unprefixed text is dropped for players.
        String inner = Messages.resolve(
                Messages.get(Messages.CHAOS_BROADCAST, Messages.CHAOS_BROADCAST_DEFAULT),
                java.util.Map.of("spectator", spectator.getName(), "target", target.getPlayer().getName()));
        match.broadcast("message:" + inner);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Messages.send(ctx.getSource().getSender(), Messages.INFO, Messages.INFO_DEFAULT);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            Messages.send(ctx.getSource().getSender(), Messages.JOIN_ONLY_PLAYERS, Messages.JOIN_ONLY_PLAYERS_DEFAULT);
            return 1;
        }
        Player player = (Player) sender;
        if (ArcadeAPIProvider.get().getQueueManager().joinQueue(player, (Game) game) != JoinResult.SUCCESS) {
            Messages.send(player, Messages.JOIN_FAILED, Messages.JOIN_FAILED_DEFAULT);
            return 1;
        }
        Messages.send(player, Messages.JOIN_SUCCESS, Messages.JOIN_SUCCESS_DEFAULT);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            Messages.send(ctx.getSource().getSender(), Messages.LEAVE_ONLY_PLAYERS, Messages.LEAVE_ONLY_PLAYERS_DEFAULT);
            return 1;
        }
        Player player = (Player) sender;
        ArcadeAPIProvider.get().getQueueManager().leaveQueue(player);
        Messages.send(player, Messages.LEAVE_SUCCESS, Messages.LEAVE_SUCCESS_DEFAULT);
        return 1;
    }

    private static IParticipant requireParticipant(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            Messages.send(ctx.getSource().getSender(), Messages.DEBUG_ONLY_PLAYERS, Messages.DEBUG_ONLY_PLAYERS_DEFAULT);
            return null;
        }
        Player player = (Player) sender;
        IParticipant participant = ArcadeAPIProvider.get().getParticipant((Game) game, player);
        if (participant == null) {
            Messages.send(player, Messages.DEBUG_NOT_IN_MATCH, Messages.DEBUG_NOT_IN_MATCH_DEFAULT);
            return null;
        }
        return participant;
    }

    private static int debugWin(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        IParticipant participant = requireParticipant(ctx, game);
        if (participant == null) {
            return 1;
        }
        participant.getMatch().endWithWinners(List.of(participant));
        Messages.send(participant.getPlayer(), Messages.DEBUG_WIN, Messages.DEBUG_WIN_DEFAULT);
        return 1;
    }

    private static int debugLose(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        IParticipant participant = requireParticipant(ctx, game);
        if (participant == null) {
            return 1;
        }
        participant.eliminate();
        Messages.send(participant.getPlayer(), Messages.DEBUG_LOSE, Messages.DEBUG_LOSE_DEFAULT);
        return 1;
    }

    private static int debugEnd(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        IParticipant participant = requireParticipant(ctx, game);
        if (participant == null) {
            return 1;
        }
        participant.getMatch().end();
        Messages.send(participant.getPlayer(), Messages.DEBUG_END, Messages.DEBUG_END_DEFAULT);
        return 1;
    }
}
