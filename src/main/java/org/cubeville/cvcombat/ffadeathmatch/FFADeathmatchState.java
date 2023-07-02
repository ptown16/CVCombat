package org.cubeville.cvcombat.ffadeathmatch;

import org.cubeville.cvcombat.models.PvPPlayerState;

public class FFADeathmatchState extends PvPPlayerState {
    // In the case where a player dies without player interference
    // We need a way to capture that a player died and remove 1 from their score
    // Without editing the kill total for the end of game stats
    int score = 0;

    @Override
    public int getSortingValue() {
        return score;
    }

    public FFADeathmatchState() {}
}