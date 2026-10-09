package org.drappula.arcadeBedrockPillars;

import org.drappula.arcadeApi.systems.game.Game;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class ArcadeBedrockPillarsTest extends PluginTest {

    @Test
    void instanceAvailable() {
        assertSame(plugin, ArcadeBedrockPillars.get());
    }

    @Test
    void registersGameOnEnable() {
        ArgumentCaptor<Game> games = ArgumentCaptor.forClass(Game.class);
        verify(gameManager).registerGame(games.capture());
        assertEquals("bedrock-pillars", games.getValue().getId());
    }

    @Test
    void defaultConfigSaved() {
        assertEquals(1, plugin.getConfig().getInt("min-players"));
        assertEquals(20, plugin.getConfig().getInt("max-players"));
        assertEquals(5, plugin.getConfig().getInt("item-interval-seconds"));
        assertEquals(20, plugin.getConfig().getStringList("items").size());
        assertFalse(plugin.getConfig().getStringList("items").stream().anyMatch(i -> i.contains("BUCKET")));
    }

    @Test
    void unregistersGameOnDisable() {
        plugin.onDisable();

        verify(gameManager).unregisterGame(any(Game.class));
    }
}
