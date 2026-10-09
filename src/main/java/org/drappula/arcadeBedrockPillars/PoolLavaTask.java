package org.drappula.arcadeBedrockPillars;

import java.util.LinkedHashMap;
import com.cryptomorin.xseries.XMaterial;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.drappula.arcadeApi.systems.map.IArcadeMap;

/**
 * Rising lava for pool-map matches. The flooded region is the map's spawn
 * bounding box plus a margin; the first layer is the {@code lava-start-y}
 * option and layers rise at {@code lava-blocks-rise-per-minute}. Unlike the
 * old procedural arenas, pool worlds persist, so every placed cell is
 * recorded and {@link #restore()} puts the original blocks back at match end.
 */
public class PoolLavaTask extends BukkitRunnable {
    static final int MARGIN = 3;

    // STATIONARY_LAVA on 1.8-1.12, LAVA after the 1.13 flattening: XMaterial resolves it.
    private static final Material LAVA = XMaterial.LAVA.parseMaterial();

    private final World world;
    private final int rate;
    private final int startY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int capY;
    private final Map<Location, Material> placed = new LinkedHashMap<Location, Material>();
    private int currentY;
    private long seconds;
    private boolean restored;

    public PoolLavaTask(IArcadeMap map) {
        List<Location> spawns = map.getSpawnPoints();
        if (spawns.isEmpty()) throw new IllegalArgumentException("Pool map " + map.getId() + " has no spawns");
        rate = map.getIntConfig(BedrockPillarsGame.LAVA_RATE_KEY);
        startY = map.getIntConfig(BedrockPillarsGame.LAVA_START_Y_KEY);
        int[] region = regionOf(map);
        minX = region[0];
        maxX = region[1];
        minZ = region[2];
        maxZ = region[3];
        world = spawns.get(0).getWorld();
        capY = world.getMaxHeight() - 1;
        currentY = startY - 1;
    }

    public static double centroidX(IArcadeMap map) {
        double sum = 0;
        for (Location spawn : map.getSpawnPoints()) sum += spawn.getX();
        return sum / map.getSpawnPoints().size();
    }

    public static double centroidZ(IArcadeMap map) {
        double sum = 0;
        for (Location spawn : map.getSpawnPoints()) sum += spawn.getZ();
        return sum / map.getSpawnPoints().size();
    }

    /**
     * Flood region: the full border square around the spawn centroid when a
     * border size is set, otherwise the spawn bounding box plus a margin.
     */
    static int[] regionOf(IArcadeMap map) {
        List<Location> spawns = map.getSpawnPoints();
        if (spawns.isEmpty()) throw new IllegalArgumentException("Pool map " + map.getId() + " has no spawns");
        int size = map.getIntConfig(BedrockPillarsGame.BORDER_SIZE_KEY);
        if (size > 0) {
            double half = size / 2.0;
            double cx = centroidX(map);
            double cz = centroidZ(map);
            return new int[]{
                    (int) Math.floor(cx - half), (int) Math.ceil(cx + half),
                    (int) Math.floor(cz - half), (int) Math.ceil(cz + half)};
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Location spawn : spawns) {
            minX = Math.min(minX, spawn.getBlockX());
            maxX = Math.max(maxX, spawn.getBlockX());
            minZ = Math.min(minZ, spawn.getBlockZ());
            maxZ = Math.max(maxZ, spawn.getBlockZ());
        }
        return new int[]{minX - MARGIN, maxX + MARGIN, minZ - MARGIN, maxZ + MARGIN};
    }

    /**
     * Advances one second of lava rise. Layer count is an exact function of
     * elapsed seconds ({@code seconds * rate / 60}), so a rate of 60 rises
     * one layer per second with no float drift. A rate of 0 cancels
     * immediately and never rises.
     */
    @Override
    public void run() {
        if (restored || rate <= 0 || currentY >= capY) {
            cancel();
            return;
        }
        seconds++;
        long target = Math.min((long) startY - 1 + seconds * rate / 60, capY);
        while (currentY < target) {
            currentY++;
            fillLayer(currentY);
        }
        if (currentY >= capY) cancel();
    }

    private void fillLayer(int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                placed.putIfAbsent(block.getLocation(), block.getType());
                block.setType(LAVA);
            }
        }
    }

    /** Cancels the rise and restores every placed cell. Safe to call twice. */
    public void restore() {
        restored = true;
        try {
            cancel();
        } catch (IllegalStateException e) {
            // Never scheduled (e.g. in tests): nothing to cancel.
        }
        for (Map.Entry<Location, Material> cell : placed.entrySet()) {
            Location at = cell.getKey();
            if (at.getWorld() == null) continue;
            at.getWorld().getBlockAt(at).setType(cell.getValue());
        }
        placed.clear();
    }
}
