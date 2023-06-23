package org.cubeville.cvcombat.deathmatch;

import org.cubeville.cvcombat.models.PvPPlayerState;
import org.cubeville.cvgames.models.PlayerState;

public class DeathmatchState extends PvPPlayerState {
    public DeathmatchState(int team) {
        super(team);
    }

    @Override
    public int getSortingValue() {
        return kills;
    }
}