package org.drappula.arcadeBedrockPillars;

import java.util.Map;
import org.bukkit.command.CommandSender;

/** Central MiniMessage-template lookup for {@code messages.*} in config.yml. */
public final class Messages {
    private Messages() {
    }

    public static final String INFO = "messages.info";
    public static final String INFO_DEFAULT = "<aqua><b>Bedrock Pillars</b></aqua> <gray>- /bp join | /bp leave";

    public static final String JOIN_ONLY_PLAYERS = "messages.join.only-players";
    public static final String JOIN_ONLY_PLAYERS_DEFAULT = "<red>Only players can join the Bedrock Pillars queue.";
    public static final String JOIN_FAILED = "messages.join.failed";
    public static final String JOIN_FAILED_DEFAULT = "<red>Failed to join the queue.";
    public static final String JOIN_SUCCESS = "messages.join.success";
    public static final String JOIN_SUCCESS_DEFAULT = "<green>Joined the Bedrock Pillars queue.";

    public static final String LEAVE_ONLY_PLAYERS = "messages.leave.only-players";
    public static final String LEAVE_ONLY_PLAYERS_DEFAULT = "<red>Only players can leave the Bedrock Pillars queue.";
    public static final String LEAVE_SUCCESS = "messages.leave.success";
    public static final String LEAVE_SUCCESS_DEFAULT = "<yellow>Left the Bedrock Pillars queue.";

    public static final String CHAOS_NOT_PLAYER = "messages.chaos.not-player";
    public static final String CHAOS_NOT_PLAYER_DEFAULT = "<red>Only spectators can cause chaos.";
    public static final String CHAOS_MUST_SPECTATE = "messages.chaos.must-spectate";
    public static final String CHAOS_MUST_SPECTATE_DEFAULT = "<red>You must be spectating a Bedrock Pillars match.";
    public static final String CHAOS_COOLDOWN = "messages.chaos.cooldown";
    public static final String CHAOS_COOLDOWN_DEFAULT = "<red>Chaos on cooldown: <seconds>s.";
    public static final String CHAOS_MATCH_CAP = "messages.chaos.match-cap";
    public static final String CHAOS_MATCH_CAP_DEFAULT = "<red>This match has had enough chaos.";
    public static final String CHAOS_BROADCAST = "messages.chaos.broadcast";
    public static final String CHAOS_BROADCAST_DEFAULT = "<light_purple><spectator> sent chaos to <target>!";

    public static final String DEBUG_ONLY_PLAYERS = "messages.debug.only-players";
    public static final String DEBUG_ONLY_PLAYERS_DEFAULT = "<red>Only players can use debug commands.";
    public static final String DEBUG_NOT_IN_MATCH = "messages.debug.not-in-match";
    public static final String DEBUG_NOT_IN_MATCH_DEFAULT = "<red>You are not in a Bedrock Pillars match.";
    public static final String DEBUG_WIN = "messages.debug.win";
    public static final String DEBUG_WIN_DEFAULT = "<green>Debug: declared winner, match ended.";
    public static final String DEBUG_LOSE = "messages.debug.lose";
    public static final String DEBUG_LOSE_DEFAULT = "<yellow>Debug: eliminated.";
    public static final String DEBUG_END = "messages.debug.end";
    public static final String DEBUG_END_DEFAULT = "<yellow>Debug: match ended with no winners.";

    public static final String QUEUE_WAITING_BOSSBAR = "messages.queue.waiting-bossbar";
    public static final String QUEUE_WAITING_BOSSBAR_DEFAULT = "Waiting for players";

    public static String get(String key, String def) {
        String value = ArcadeBedrockPillars.get().getConfig().getString(key);
        return value != null ? value : def;
    }

    /** Replaces {@code <name>} tokens; unknown tokens are left as-is. */
    public static String resolve(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    public static void send(CommandSender sender, String key, String def) {
        sender.sendRichMessage(get(key, def));
    }

    public static void send(CommandSender sender, String key, String def, Map<String, String> placeholders) {
        sender.sendRichMessage(resolve(get(key, def), placeholders));
    }

    public static net.kyori.adventure.text.Component bossbar(String key, String def,
            Map<String, String> placeholders) {
        String resolved = resolve(get(key, def), placeholders);
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(resolved);
    }
}
