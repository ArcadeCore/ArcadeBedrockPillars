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
            ctx.getSource().getSender().sendRichMessage("<red>Only spectators can cause chaos.");
            return 1;
        }
        var match = ArcadeAPIProvider.get().getMatchManager().getMatch(spectator).orElse(null);
        if (match == null || !match.getGame().getId().equals(game.getId()) || !match.isSpectating(spectator)) {
            spectator.sendRichMessage("<red>You must be spectating a Bedrock Pillars match.");
            return 1;
        }
        var cfg = ArcadeBedrockPillars.get().getConfig();
        long cooldownMs = cfg.getLong("chaos.cooldown-seconds", 20) * 1000L;
        int cap = cfg.getInt("chaos.max-per-match", 10);
        long now = System.currentTimeMillis();
        Long last = game.chaosLast.get(spectator.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            spectator.sendRichMessage("<red>Chaos on cooldown: " + ((cooldownMs - (now - last)) / 1000 + 1) + "s.");
            return 1;
        }
        if (game.chaosUses.getOrDefault(match, 0) >= cap) {
            spectator.sendRichMessage("<red>This match has had enough chaos.");
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
        match.broadcast("message:<light_purple>" + spectator.getName() + " sent chaos to " + target.getPlayer().getName() + "!");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendRichMessage("<aqua><b>Bedrock Pillars</b></aqua> <gray>- /bp join | /bp leave");
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendRichMessage("<red>Only players can join the Bedrock Pillars queue.");
            return 1;
        }
        Player player = (Player) sender;
        if (ArcadeAPIProvider.get().getQueueManager().joinQueue(player, (Game) game) != JoinResult.SUCCESS) {
            player.sendRichMessage("<red>Failed to join the queue.");
            return 1;
        }
        player.sendRichMessage("<green>Joined the Bedrock Pillars queue.");
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendRichMessage("<red>Only players can leave the Bedrock Pillars queue.");
            return 1;
        }
        Player player = (Player) sender;
        ArcadeAPIProvider.get().getQueueManager().leaveQueue(player);
        player.sendRichMessage("<yellow>Left the Bedrock Pillars queue.");
        return 1;
    }

    private static IParticipant requireParticipant(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendRichMessage("<red>Only players can use debug commands.");
            return null;
        }
        Player player = (Player) sender;
        IParticipant participant = ArcadeAPIProvider.get().getParticipant((Game) game, player);
        if (participant == null) {
            player.sendRichMessage("<red>You are not in a Bedrock Pillars match.");
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
        participant.getPlayer().sendRichMessage("<green>Debug: declared winner, match ended.");
        return 1;
    }

    private static int debugLose(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        IParticipant participant = requireParticipant(ctx, game);
        if (participant == null) {
            return 1;
        }
        participant.eliminate();
        participant.getPlayer().sendRichMessage("<yellow>Debug: eliminated.");
        return 1;
    }

    private static int debugEnd(CommandContext<CommandSourceStack> ctx, BedrockPillarsGame game) {
        IParticipant participant = requireParticipant(ctx, game);
        if (participant == null) {
            return 1;
        }
        participant.getMatch().end();
        participant.getPlayer().sendRichMessage("<yellow>Debug: match ended with no winners.");
        return 1;
    }
}
