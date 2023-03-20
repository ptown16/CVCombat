package org.cubeville.cvcombat.deathmatch;

import org.cubeville.cvgames.models.PlayerState;

public class DeathmatchState extends PlayerState {
    int team;
    int kills = 0;
    int deaths = 0;
    Integer selectedKit;
    int respawnTimer = -1;

    public DeathmatchState(int team) {
        this.team = team;
    }

    @Override
    public int getSortingValue() {
        return kills;
    }
}