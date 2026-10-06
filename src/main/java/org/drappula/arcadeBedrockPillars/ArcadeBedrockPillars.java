package org.drappula.arcadeBedrockPillars;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.systems.game.Game;

// NOTE: the 1.0.0 release jar declares this class final. The modifier is
// dropped here so MockBukkit can load the plugin in tests (it subclasses the
// plugin class at load time). No runtime behavior changes.
public class ArcadeBedrockPillars extends JavaPlugin {
    private static ArcadeBedrockPillars instance;
    private final BedrockPillarsGame game = new BedrockPillarsGame();

    public static ArcadeBedrockPillars get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ArcadeAPIProvider.get().getGameManager().registerGame((Game) game);
        getServer().getPluginManager().registerEvents((Listener) new BedrockPillarsListener(game), (Plugin) this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                commands -> ((Commands) commands.registrar()).register(BedrockPillarsCommand.get(game)));
    }

    @Override
    public void onDisable() {
        ArcadeAPIProvider.get().getGameManager().unregisterGame((Game) game);
    }
}
