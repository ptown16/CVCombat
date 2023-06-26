package org.cubeville.cvcombat.bedwars;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPPlayerState;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class Bedwars extends PvPTeamSelectorGame {
    public Bedwars(String id, String arenaName) {
        super(id, arenaName);
    }

    @Override
    public PvPGameOptions getOptions() {
        PvPGameOptions pvpGameOptions = new PvPGameOptions();
        pvpGameOptions.setKitsEnabled(false);
        pvpGameOptions.setDefaultKits(false);
        return pvpGameOptions;
    }

    @Override
    public List<Integer[]> getSortedTeams() {
        return null;
    }

    @Override
    public void onGameStart(List<Set<Player>> list) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> players = list.get(i);

            if (players == null) {
                continue;
            }
            for (Player player : players) {
                player.sendMessage("You've joined the " + team.get("name") + "§r.");
                state.put(player, new BedwarsState(i));
            }

        }
        super.onPvPGameStart(list);
    }

    @Override
    public void onPlayerLeave(Player player) {

    }

    @Override
    protected BedwarsState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof BedwarsState)) return null;
        return (BedwarsState) state.get(p);
    }

    @Override
    public void onGameFinish() {

    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent deathEvent) {
        Player playerSlayer = deathEvent.getEntity().getKiller();
        Player playerLoser = deathEvent.getEntity();
        BedwarsState playerSlayerState = getState(playerSlayer);
        BedwarsState playerLoserState = getState(playerLoser);
        if (playerSlayerState == null || playerLoserState == null) {
            return;
        }
        playerLoser.sendMessage(teams.get(playerSlayerState.team).get("chat-color") + "You're a loser, you should go play bury berry with your momma.");
    }
}
