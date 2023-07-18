package org.cubeville.cvcombat.teamelimination;

import org.cubeville.cvcombat.models.PvPTeamPlayerState;

public class TeamEliminationState extends PvPTeamPlayerState {

    public boolean isAlive = true;
    public TeamEliminationState(int team) {
        super(team);
    }
}
