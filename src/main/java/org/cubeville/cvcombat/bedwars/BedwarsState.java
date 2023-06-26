package org.cubeville.cvcombat.bedwars;

import org.cubeville.cvcombat.models.PvPPlayerState;

public class BedwarsState extends PvPPlayerState {
    public BedwarsState(int team) {
        super(team);
    }

    @Override
    public int getSortingValue() {
        return 0;
    }
}
