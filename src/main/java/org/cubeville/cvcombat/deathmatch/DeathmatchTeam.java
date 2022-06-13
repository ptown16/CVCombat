package org.cubeville.cvcombat.deathmatch;

import org.cubeville.cvgames.vartypes.*;

public class DeathmatchTeam extends GameVariableTeam {
    public DeathmatchTeam() {
        super("DeathmatchTeam");
        addField("loadout-team", new GameVariableString());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
        addField("kit-lobby", new GameVariableLocation());
    }
}
