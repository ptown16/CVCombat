package org.cubeville.cvcombat;

import org.cubeville.cvgames.vartypes.GameVariableItem;
import org.cubeville.cvgames.vartypes.GameVariableObject;
import org.cubeville.cvgames.vartypes.GameVariableString;

public class GameVariableKit extends GameVariableObject {
    public GameVariableKit() {
        super("Kit");
        addField("name", new GameVariableString());
        addField("item", new GameVariableItem());
        addField("loadout", new GameVariableString());
    }
}
