package org.drappula.arcadeBedrockPillars;

import org.bukkit.Material;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemDropTaskTest extends PluginTest {

    private IParticipant participant(PlayerMock player, boolean eliminated) {
        IParticipant participant = mock(IParticipant.class);
        when(participant.getPlayer()).thenReturn(player);
        when(participant.isEliminated()).thenReturn(eliminated);
        return participant;
    }

    private int itemCount(PlayerMock player) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(item -> item != null && item.getType() != Material.AIR)
                .mapToInt(item -> item.getAmount())
                .sum();
    }

    @Test
    void dropsItemsToEveryAliveParticipant() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        IParticipant firstParticipant = participant(first, false);
        IParticipant secondParticipant = participant(second, false);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(firstParticipant, secondParticipant));

        new ItemDropTask(match).run();

        assertTrue(itemCount(first) > 0);
        assertTrue(itemCount(second) > 0);
    }

    @Test
    void skipsEliminatedParticipants() {
        PlayerMock alive = server.addPlayer();
        PlayerMock out = server.addPlayer();
        IParticipant aliveParticipant = participant(alive, false);
        IParticipant outParticipant = participant(out, true);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(aliveParticipant, outParticipant));

        new ItemDropTask(match).run();

        assertTrue(itemCount(alive) > 0);
        assertEquals(0, itemCount(out));
    }

    @Test
    void invalidMaterialsAreSkipped() {
        plugin.getConfig().set("items", List.of("NOT_A_REAL_MATERIAL", "ARROW"));
        PlayerMock player = server.addPlayer();
        IParticipant solo = participant(player, false);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(solo));

        new ItemDropTask(match).run();

        assertNotNull(player.getInventory().getItem(0));
        assertEquals(Material.ARROW, player.getInventory().getItem(0).getType());
        assertEquals(8, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void projectilesDropInStacksOfEight() {
        plugin.getConfig().set("items", List.of("SNOWBALL"));
        PlayerMock player = server.addPlayer();
        IParticipant solo = participant(player, false);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(solo));

        new ItemDropTask(match).run();

        assertEquals(Material.SNOWBALL, player.getInventory().getItem(0).getType());
        assertEquals(8, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void singleItemsDropSingly() {
        plugin.getConfig().set("items", List.of("SHIELD"));
        PlayerMock player = server.addPlayer();
        IParticipant solo = participant(player, false);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(solo));

        new ItemDropTask(match).run();

        assertEquals(Material.SHIELD, player.getInventory().getItem(0).getType());
        assertEquals(1, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void emptyPoolIsNoOp() {
        plugin.getConfig().set("items", List.of());
        PlayerMock player = server.addPlayer();
        IParticipant solo = participant(player, false);
        IMatch match = mock(IMatch.class);
        when(match.getParticipants()).thenReturn(List.of(solo));

        new ItemDropTask(match).run();

        assertEquals(0, itemCount(player));
    }
}
