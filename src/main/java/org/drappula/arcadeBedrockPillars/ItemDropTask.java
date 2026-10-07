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
    private final List<Material> pool;

    public ItemDropTask(IMatch match) {
        this.match = match;
        List<String> names = ArcadeBedrockPillars.get().getConfig().getStringList("items");
        pool = new ArrayList<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                continue;
            }
            pool.add(material);
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
        Material material = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int amount = switch (material) {
            case ARROW, SNOWBALL, EGG -> 8;
            default -> 1;
        };
        player.getInventory().addItem(new ItemStack(material, amount));
        return true;
    }
}
