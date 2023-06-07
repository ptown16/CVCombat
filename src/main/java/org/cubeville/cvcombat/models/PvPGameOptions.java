package org.cubeville.cvcombat.models;

public class PvPGameOptions {
    Boolean kitsEnabled = true;
    Boolean defaultKits = true;
    Boolean respawnEnabled = true;

    public PvPGameOptions() {
    }

    public Boolean getKitsEnabled() {
        return kitsEnabled;
    }

    public void setKitsEnabled(Boolean kitsEnabled) {
        this.kitsEnabled = kitsEnabled;
    }

    public Boolean getDefaultKits() {
        return defaultKits;
    }

    public void setDefaultKits(Boolean defaultKits) {
        this.defaultKits = defaultKits;
    }

    public Boolean getRespawnEnabled() {
        return respawnEnabled;
    }

    public void setRespawnEnabled(Boolean respawnEnabled) {
        this.respawnEnabled = respawnEnabled;
    }
}
