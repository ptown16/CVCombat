package org.cubeville.cvcombat.deathmatch;

import org.cubeville.cvgames.vartypes.*;

public class DeathmatchTeam extends GameVariableObject {
    public DeathmatchTeam() {
        super("DeathmatchTeam");
        addField("name", new GameVariableString());
        addField("chat-color", new GameVariableChatColor());
        addField("loadout-team", new GameVariableString());
        addField("tps", new GameVariableList<>(GameVariableLocation.class));
        addField("kit-lobby", new GameVariableLocation());
    }
}
