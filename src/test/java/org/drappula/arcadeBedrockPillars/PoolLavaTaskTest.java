package org.drappula.arcadeBedrockPillars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.drappula.arcadeApi.systems.map.IArcadeMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoolLavaTaskTest extends PluginTest {

    private IArcadeMap poolMap(Map<String, String> overrides) {
        List<Location> spawns = List.of(
                new Location(arenaWorld, 0, 80, 0),
                new Location(arenaWorld, 10, 90, 5));
        return new IArcadeMap() {
            @Override
            public String getId() {
                return "void";
            }

            @Override
            public String getGameId() {
                return "bedrock-pillars";
            }

            @Override
            public String getDisplayName() {
                return "Void";
            }

            @Override
            public String getWorldName() {
                return "arena-world";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public boolean isInUse() {
                return false;
            }

            @Override
            public List<Location> getSpawnPoints() {
                return spawns;
            }

            @Override
            public org.bukkit.World getWorld() {
                return arenaWorld;
            }

            @Override
            public Map<String, String> getConfigOverrides() {
                return overrides;
            }
        };
    }

    @Test
    void risesFromStartLayerAtRate() {
        IArcadeMap map = poolMap(Map.of("lava-blocks-rise-per-minute", "60"));
        new PoolLavaTask(map).runTaskTimer(plugin, 0, 20);

        // Default start-y 64: first layer after 1 second, next after another.
        server.getScheduler().performTicks(1);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(0, 64, 0).getType());
        assertEquals(Material.AIR, arenaWorld.getBlockAt(5, 65, 0).getType());

        server.getScheduler().performTicks(20);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(5, 65, 0).getType());
    }

    @Test
    void lavaSpansFullBorder() {
        IArcadeMap map = poolMap(Map.of(
                "lava-blocks-rise-per-minute", "60",
                "lava-start-y", "70",
                "border-size", "40"));
        new PoolLavaTask(map).runTaskTimer(plugin, 0, 20);

        // Border square: centroid (5, 2.5), half-size 20.
        server.getScheduler().performTicks(1);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(-15, 70, -18).getType());
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(25, 70, 23).getType());
        assertEquals(Material.AIR, arenaWorld.getBlockAt(-16, 70, -18).getType());
        assertEquals(Material.AIR, arenaWorld.getBlockAt(26, 70, 23).getType());
    }

    @Test
    void zeroRateNeverRises() {        IArcadeMap map = poolMap(Map.of("lava-blocks-rise-per-minute", "0"));
        new PoolLavaTask(map).runTaskTimer(plugin, 0, 20);

        server.getScheduler().performTicks(180 * 20);

        assertEquals(Material.AIR, arenaWorld.getBlockAt(0, 64, 0).getType());
    }

    @Test
    void restoreReturnsOriginalBlocks() {
        arenaWorld.getBlockAt(2, 65, 2).setType(Material.STONE);
        IArcadeMap map = poolMap(Map.of("lava-blocks-rise-per-minute", "60"));
        PoolLavaTask task = new PoolLavaTask(map);
        task.runTaskTimer(plugin, 0, 20);
        server.getScheduler().performTicks(40);
        assertEquals(Material.LAVA, arenaWorld.getBlockAt(2, 65, 2).getType());

        task.restore();

        assertEquals(Material.AIR, arenaWorld.getBlockAt(2, 64, 2).getType());
        assertEquals(Material.STONE, arenaWorld.getBlockAt(2, 65, 2).getType());
    }
}
