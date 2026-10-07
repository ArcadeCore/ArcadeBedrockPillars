package org.drappula.arcadeBedrockPillars;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockPillarsGameTest extends PluginTest {

    private final BedrockPillarsGame game = new BedrockPillarsGame();

    @Test
    void identity() {
        assertEquals("bedrock-pillars", game.getId());
        assertEquals("Bedrock Pillars", game.getDisplayName());
    }

    @Test
    void playerCountsFollowConfig() {
        assertEquals(1, game.getMinPlayers());
        assertEquals(20, game.getMaxPlayers());
        // playersRequired delegates to min-players, not a fixed constant.
        assertEquals(game.getMinPlayers(), game.getPlayersRequired());

        plugin.getConfig().set("min-players", 4);
        plugin.getConfig().set("max-players", 8);
        assertEquals(4, game.getMinPlayers());
        assertEquals(8, game.getMaxPlayers());
        assertEquals(4, game.getPlayersRequired());
    }

    @Test
    void createArenaUsesPool() {
        PlayerMock player = server.addPlayer();

        assertNull(game.createArena(arenaWorld, List.of(player)));
    }

    @Test
    void registersPoolOptions() {
        var options = game.getMapConfigOptions();

        assertEquals(4, options.size());
        assertEquals("lava-blocks-rise-per-minute", options.get(0).key());
        assertEquals("2", options.get(0).defaultValue());
        assertEquals("lava-start-y", options.get(1).key());
        assertEquals("64", options.get(1).defaultValue());
        assertEquals("border-size", options.get(2).key());
        assertEquals("0", options.get(2).defaultValue());
        assertEquals("item-interval-seconds", options.get(3).key());
        assertEquals("0", options.get(3).defaultValue());
    }
}
