package org.cubeville.cvcombat.capturetheflag;

import org.cubeville.cvcombat.models.PvPTeamPlayerState;

public class CaptureTheFlagState extends PvPTeamPlayerState {

    public Integer flags = 0;
    public Integer heldFlag = -1;
    public CaptureTheFlagState(int team) {
        super(team);
    }

    @Override
    public int getSortingValue() {
        return 0;
    }
}
