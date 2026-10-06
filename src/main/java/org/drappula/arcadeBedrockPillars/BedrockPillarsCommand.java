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
                .then(Commands.literal("debug")
                        .requires(source -> source.getSender().hasPermission("bedrockpillars.debug"))
                        .then(Commands.literal("win").executes(ctx -> debugWin(ctx, game)))
                        .then(Commands.literal("lose").executes(ctx -> debugLose(ctx, game)))
                        .then(Commands.literal("end").executes(ctx -> debugEnd(ctx, game)))))
                .build();
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
