package org.drappula.arcadeBedrockPillars;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.events.MatchEndEvent;
import org.drappula.arcadeApi.events.MatchStateChangeEvent;
import org.drappula.arcadeApi.events.ParticipantEliminateEvent;
import org.drappula.arcadeApi.events.QueueEnterEvent;
import org.drappula.arcadeApi.message.Messages;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.game.MatchState;
import org.drappula.arcadeApi.systems.map.IArcadeMap;

public class BedrockPillarsListener implements Listener {
    private final BedrockPillarsGame game;
    private final Map<IMatch, ItemDropTask> itemTasks = new HashMap<IMatch, ItemDropTask>();
    private final Map<IMatch, PoolLavaTask> lavaTasks = new HashMap<IMatch, PoolLavaTask>();
    private final Map<IMatch, BorderState> borders = new HashMap<IMatch, BorderState>();
    private static final class BorderState {
        final double x, z, size;

        BorderState(double x, double z, double size) {
            this.x = x;
            this.z = z;
            this.size = size;
        }
    }

    public BedrockPillarsListener(BedrockPillarsGame game) {
        this.game = game;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        IParticipant participant = ArcadeAPIProvider.get().getParticipant((Game) game, event.getEntity());
        if (participant != null && !participant.isEliminated()) {
            participant.eliminate(event.getEntity().getKiller());
        }
    }

    private void recordStats(IMatch match, IParticipant victim, Player killer) {
        org.drappula.arcadeApi.database.IGameStatsManager stats = ArcadeAPIProvider.get().getStatsManager();
        try {
            if (match.getStartedAt() != null) {
                long secs = java.time.Duration.between(match.getStartedAt(), java.time.Instant.now()).getSeconds();
                stats.addStat(victim.getPlayer().getUniqueId(), victim.getPlayer().getName(),
                        game.getId(), "survival_seconds", (int) secs);
            }
            if (killer != null && !killer.equals(victim.getPlayer())) {
                stats.addStat(killer.getUniqueId(), killer.getName(), game.getId(), "kills", 1);
            }
        } catch (java.sql.SQLException e) {
            ArcadeBedrockPillars.get().getLogger().log(java.util.logging.Level.SEVERE, "Failed to record Bedrock Pillars stats", e);
        }
    }

    @EventHandler
    public void onParticipantEliminate(ParticipantEliminateEvent event) {
        IMatch match = event.getParticipant().getMatch();
        if (!match.getGame().getId().equals(game.getId())) {
            return;
        }
        recordStats(match, event.getParticipant(), event.getKiller());
        // The victim still counts as alive while this event fires, so decide
        // next tick once elimination bookkeeping has completed.
        Bukkit.getScheduler().runTask((Plugin) ArcadeBedrockPillars.get(), () -> {
            if (match.getAliveCount() == 1) {
                match.endWithWinners(match.getAliveParticipants());
            } else if (match.getAliveCount() == 0) {
                match.end();
            }
        });
    }

    @EventHandler
    public void onMatchStateChange(MatchStateChangeEvent event) {
        if (event.getNewState() != MatchState.STARTED) {
            return;
        }
        IMatch match = event.getMatch();
        if (!match.getGame().getId().equals(game.getId())) {
            return;
        }
        IArcadeMap map = match.getMap();
        if (map != null && map.getWorld() != null) {
            PoolLavaTask lavaTask = new PoolLavaTask(map);
            lavaTask.runTaskTimer((Plugin) ArcadeBedrockPillars.get(), 20L, 20L);
            lavaTasks.put(match, lavaTask);
            int borderSize = map.getIntConfig(BedrockPillarsGame.BORDER_SIZE_KEY);
            if (borderSize > 0) {
                org.bukkit.WorldBorder border = map.getWorld().getWorldBorder();
                borders.put(match, new BorderState(
                        border.getCenter().getX(), border.getCenter().getZ(), border.getSize()));
                border.setCenter(PoolLavaTask.centroidX(map), PoolLavaTask.centroidZ(map));
                border.setSize(borderSize);
            }
        }
        int mapInterval = map != null ? map.getIntConfig(BedrockPillarsGame.ITEM_INTERVAL_KEY) : 0;
        int itemInterval = mapInterval > 0 ? mapInterval
                : Math.max(1, ArcadeBedrockPillars.get().getConfig().getInt("item-interval-seconds"));
        ItemDropTask itemTask = new ItemDropTask(match);
        itemTask.runTaskTimer((Plugin) ArcadeBedrockPillars.get(), (long) itemInterval * 20L, (long) itemInterval * 20L);
        itemTasks.put(match, itemTask);
    }

    @EventHandler
    public void onQueueEnter(QueueEnterEvent event) {
        if (!event.getGame().getId().equals(game.getId())) {
            return;
        }
        // No boss bar on 1.8, so the waiting hint is an action bar message everywhere.
        Messages.send(event.getPlayer(), "actionbar:<blue>Waiting for players");
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        IMatch match = event.getMatch();
        if (!match.getGame().getId().equals(game.getId())) {
            return;
        }
        game.chaosUses.remove(match);
        ItemDropTask itemTask = itemTasks.remove(match);
        if (itemTask != null) {
            itemTask.cancel();
        }
        PoolLavaTask lavaTask = lavaTasks.remove(match);
        if (lavaTask != null) {
            lavaTask.restore();
        }
        BorderState previous = borders.remove(match);
        if (previous != null && match.getMap() != null && match.getMap().getWorld() != null) {
            org.bukkit.WorldBorder border = match.getMap().getWorld().getWorldBorder();
            border.setCenter(previous.x, previous.z);
            border.setSize(previous.size);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isFrozen(Player player) {
        IParticipant participant = ArcadeAPIProvider.get().getParticipant((Game) game, player);
        return participant != null && !participant.isEliminated()
                && participant.getMatch().getState() == MatchState.STARTING;
    }
}
