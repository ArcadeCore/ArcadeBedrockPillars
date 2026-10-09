package org.drappula.arcadeBedrockPillars;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;

public class ItemDropTask extends BukkitRunnable {
    private final IMatch match;
    private final List<ItemStack> pool;

    public ItemDropTask(IMatch match) {
        this.match = match;
        pool = new ArrayList<>();
        for (String entry : ArcadeBedrockPillars.get().getConfig().getStringList("items")) {
            // "MATERIAL" or "MATERIAL:amount"; unknown materials are skipped.
            String[] parts = entry.split(":", 2);
            Material material = Material.matchMaterial(parts[0].trim());
            if (material == null) {
                continue;
            }
            int amount = switch (material) {
                case ARROW, SNOWBALL, EGG -> 8;
                default -> 1;
            };
            if (parts.length == 2) {
                try {
                    amount = Math.max(1, Math.min(material.getMaxStackSize(), Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                    // keep the default amount
                }
            }
            pool.add(new ItemStack(material, amount));
        }
    }

    @Override
    public void run() {
        if (pool.isEmpty()) {
            return;
        }
        for (IParticipant participant : match.getParticipants()) {
            if (participant.isEliminated()) {
                continue;
            }
            give(participant.getPlayer());
        }
    }

    /** Gives one random pool item to {@code player}; false if the pool is empty. */
    public boolean give(org.bukkit.entity.Player player) {
        if (pool.isEmpty()) {
            return false;
        }
        player.getInventory().addItem(pool.get(ThreadLocalRandom.current().nextInt(pool.size())).clone());
        return true;
    }
}
