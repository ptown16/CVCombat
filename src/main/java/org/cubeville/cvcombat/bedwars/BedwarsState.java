package org.cubeville.cvcombat.bedwars;

import org.cubeville.cvcombat.models.PvPTeamPlayerState;

public class BedwarsState extends PvPTeamPlayerState {
    public BedwarsState(int team) {
        super(team);
    }

    @Override
    public int getSortingValue() {
        return 0;
    }
}
