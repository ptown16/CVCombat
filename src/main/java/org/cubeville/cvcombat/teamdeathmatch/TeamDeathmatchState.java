package org.cubeville.cvcombat.teamdeathmatch;

import org.cubeville.cvcombat.models.PvPTeamPlayerState;

public class TeamDeathmatchState extends PvPTeamPlayerState {
    public TeamDeathmatchState(int team) {
        super(team);
    }

    @Override
    public int getSortingValue() {
        return kills;
    }
}