package org.drappula.arcadeBedrockPillars;

import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.map.MapConfigOption;

public class BedrockPillarsGame implements Game {
    /** Blocks of lava risen per minute; 0 disables the rise for a map. */
    public static final String LAVA_RATE_KEY = "lava-blocks-rise-per-minute";
    /** Y of the first lava layer, measured in the pool map's world. */
    public static final String LAVA_START_Y_KEY = "lava-start-y";
    /** World border size during the match; 0 leaves the border alone. */
    public static final String BORDER_SIZE_KEY = "border-size";
    /** Seconds between item drops; 0 falls back to the global {@code item-interval-seconds}. */
    public static final String ITEM_INTERVAL_KEY = "item-interval-seconds";

    /** Spectator chaos uses so far, per match; cleared on match end. */
    final java.util.Map<org.drappula.arcadeApi.systems.game.IMatch, Integer> chaosUses = new java.util.HashMap<org.drappula.arcadeApi.systems.game.IMatch, Integer>();
    /** Last chaos use per spectator (epoch ms). */
    final java.util.Map<java.util.UUID, Long> chaosLast = new java.util.HashMap<java.util.UUID, Long>();

    @Override
    public String getId() {
        return "bedrock-pillars";
    }

    @Override
    public String getDisplayName() {
        return "Bedrock Pillars";
    }

    @Override
    public int getPlayersRequired() {
        return getMinPlayers();
    }

    @Override
    public int getMinPlayers() {
        return ArcadeBedrockPillars.get().getConfig().getInt("min-players");
    }

    @Override
    public int getMaxPlayers() {
        return ArcadeBedrockPillars.get().getConfig().getInt("max-players");
    }

    @Override
    public java.util.List<MapConfigOption> getMapConfigOptions() {
        return java.util.Arrays.asList(
                MapConfigOption.integer(LAVA_RATE_KEY, 2, 0, 60),
                MapConfigOption.integer(LAVA_START_Y_KEY, 64, -64, 320),
                MapConfigOption.integer(BORDER_SIZE_KEY, 0, 0, 60000000),
                MapConfigOption.integer(ITEM_INTERVAL_KEY, 0, 0, 3600));
    }
}
