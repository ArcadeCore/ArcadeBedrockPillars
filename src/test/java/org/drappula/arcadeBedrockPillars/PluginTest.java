package org.drappula.arcadeBedrockPillars;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.drappula.arcadeApi.ArcadeAPI;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.systems.game.IGameManager;
import org.drappula.arcadeApi.systems.queue.IQueueManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Boots MockBukkit, registers a mocked ArcadeAPI, and loads the real plugin
 * so game/config/command/scheduler paths run for real.
 */
public abstract class PluginTest {

    protected ServerMock server;
    protected ArcadeBedrockPillars plugin;
    protected ArcadeAPI api;
    protected IGameManager gameManager;
    protected IQueueManager queueManager;
    /**
     * Tall empty world for block-level tests. MockBukkit's default worlds cap
     * at y=128, so tests that need headroom use this 320-high world instead.
     */
    protected WorldMock arenaWorld;

    @BeforeEach
    void bootPlugin() {
        server = MockBukkit.mock();
        arenaWorld = new WorldMock(Material.AIR, Biome.PLAINS, -64, 320, -64);
        arenaWorld.setName("arena-world");
        server.addWorld(arenaWorld);
        api = mock(ArcadeAPI.class);
        gameManager = mock(IGameManager.class);
        queueManager = mock(IQueueManager.class);
        when(api.getGameManager()).thenReturn(gameManager);
        when(api.getQueueManager()).thenReturn(queueManager);
        // The game is registered in production, so map config lookups resolve.
        when(gameManager.getGame("bedrock-pillars")).thenReturn(new BedrockPillarsGame());
        ArcadeAPIProvider.register(api);
        plugin = MockBukkit.load(ArcadeBedrockPillars.class);
        plugin.getConfig().set("arena.world", "arena-world");
    }

    @AfterEach
    void tearDownPlugin() {
        MockBukkit.unmock();
        ArcadeAPIProvider.unregister();
    }
}
