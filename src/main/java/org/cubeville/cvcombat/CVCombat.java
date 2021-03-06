package org.cubeville.cvcombat;

import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvcombat.deathmatch.Deathmatch;
import org.cubeville.cvgames.CVGames;

public final class CVCombat extends JavaPlugin {

    private static CVCombat instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        CVGames.gameManager().registerGame("deathmatch", Deathmatch.class);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static CVCombat getInstance() { return instance; }

}
