package org.drappula.arcadeBedrockPillars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.message.Messages;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.queue.JoinResult;

/** {@code /bp}. A plain Bukkit executor so it works on every server version. */
public class BedrockPillarsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> PUBLIC = Arrays.asList("join", "leave", "chaos");
    private static final List<String> DEBUG = Arrays.asList("win", "lose", "end");

    private final BedrockPillarsGame game;

    public BedrockPillarsCommand(BedrockPillarsGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Messages.chat(sender, "<aqua><b>Bedrock Pillars</b></aqua> <gray>- /bp join | /bp leave");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join":
                join(sender);
                return true;
            case "leave":
                leave(sender);
                return true;
            case "chaos":
                chaos(sender);
                return true;
            case "debug":
                if (!sender.hasPermission("bedrockpillars.debug")) {
                    Messages.chat(sender, "<red>You do not have permission to use this command.");
                } else if (args.length >= 2) {
                    debug(sender, args[1].toLowerCase(Locale.ROOT));
                }
                return true;
            default:
                Messages.chat(sender, "<red>Usage: /bp [join|leave|chaos]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            options.addAll(PUBLIC);
            if (sender.hasPermission("bedrockpillars.debug")) options.add("debug");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug") && sender.hasPermission("bedrockpillars.debug")) {
            options.addAll(DEBUG);
        } else {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(option);
        }
        return out;
    }

    /** Spectators drop a random item on a random surviving player. Limited by cooldown and per-match cap. */
    private void chaos(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Only spectators can cause chaos.");
            return;
        }
        Player spectator = (Player) sender;
        IMatch match = ArcadeAPIProvider.get().getMatchManager().getMatch(spectator).orElse(null);
        if (match == null || !match.getGame().getId().equals(game.getId()) || !match.isSpectating(spectator)) {
            Messages.chat(spectator, "<red>You must be spectating a Bedrock Pillars match.");
            return;
        }
        org.bukkit.configuration.file.FileConfiguration cfg = ArcadeBedrockPillars.get().getConfig();
        long cooldownMs = cfg.getLong("chaos.cooldown-seconds", 20) * 1000L;
        int cap = cfg.getInt("chaos.max-per-match", 10);
        long now = System.currentTimeMillis();
        Long last = game.chaosLast.get(spectator.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            Messages.chat(spectator, "<red>Chaos on cooldown: " + ((cooldownMs - (now - last)) / 1000 + 1) + "s.");
            return;
        }
        Integer used = game.chaosUses.get(match);
        if (used != null && used >= cap) {
            Messages.chat(spectator, "<red>This match has had enough chaos.");
            return;
        }
        List<IParticipant> alive = match.getAliveParticipants();
        if (alive.isEmpty()) {
            return;
        }
        IParticipant target = alive.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(alive.size()));
        if (!new ItemDropTask(match).give(target.getPlayer())) {
            return;
        }
        game.chaosLast.put(spectator.getUniqueId(), now);
        game.chaosUses.put(match, used == null ? 1 : used + 1);
        // Core routes by prefix; unprefixed text is dropped for players. Names are placeholders, not markup.
        match.broadcast("message:<light_purple><spectator> sent chaos to <target>!",
                "spectator", spectator.getName(), "target", target.getPlayer().getName());
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Only players can join the Bedrock Pillars queue.");
            return;
        }
        Player player = (Player) sender;
        if (ArcadeAPIProvider.get().getQueueManager().joinQueue(player, (Game) game) != JoinResult.SUCCESS) {
            Messages.chat(player, "<red>Failed to join the queue.");
            return;
        }
        Messages.chat(player, "<green>Joined the Bedrock Pillars queue.");
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Only players can leave the Bedrock Pillars queue.");
            return;
        }
        Player player = (Player) sender;
        ArcadeAPIProvider.get().getQueueManager().leaveQueue(player);
        Messages.chat(player, "<yellow>Left the Bedrock Pillars queue.");
    }

    private IParticipant requireParticipant(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Only players can use debug commands.");
            return null;
        }
        Player player = (Player) sender;
        IParticipant participant = ArcadeAPIProvider.get().getParticipant((Game) game, player);
        if (participant == null) {
            Messages.chat(player, "<red>You are not in a Bedrock Pillars match.");
        }
        return participant;
    }

    private void debug(CommandSender sender, String action) {
        if (!DEBUG.contains(action)) return;
        IParticipant participant = requireParticipant(sender);
        if (participant == null) return;
        Player player = participant.getPlayer();
        switch (action) {
            case "win":
                participant.getMatch().endWithWinners(Collections.singletonList(participant));
                Messages.chat(player, "<green>Debug: declared winner, match ended.");
                break;
            case "lose":
                participant.eliminate();
                Messages.chat(player, "<yellow>Debug: eliminated.");
                break;
            default:
                participant.getMatch().end();
                Messages.chat(player, "<yellow>Debug: match ended with no winners.");
        }
    }
}
