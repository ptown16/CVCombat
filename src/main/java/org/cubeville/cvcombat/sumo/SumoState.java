package org.cubeville.cvcombat.sumo;

import org.bukkit.entity.Player;
import org.cubeville.cvgames.models.PlayerState;

public class SumoState extends PlayerState {
    boolean isAlive = true;
    @Override
    public int getSortingValue() {
        return 0;
    }
}
