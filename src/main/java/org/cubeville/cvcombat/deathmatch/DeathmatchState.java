package org.cubeville.cvcombat.deathmatch;

public class DeathmatchState {
    int team;
    int kills = 0;
    int deaths = 0;
    Integer selectedKit;
    boolean selectingKit = true;
    boolean hasDied = false;
    int respawnTimer = -1;

    public DeathmatchState(int team) {
        this.team = team;
    }

}
