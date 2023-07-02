package org.cubeville.cvcombat.models;

public abstract class PvPTeamPlayerState extends PvPPlayerState {
    public int team;

    public PvPTeamPlayerState(int team) {
        this.team = team;
    }
}
