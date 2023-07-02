package org.cubeville.cvcombat.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.GameVariableInt;
import org.cubeville.cvgames.vartypes.GameVariableList;
import org.cubeville.cvgames.vartypes.GameVariableLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Bedwars extends PvPTeamSelectorGame {

    private int generatorTask = -1;
    private int secondsElapsed = 0;
    private int diamondGeneratorLevel = 1;
    public Bedwars(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("diamond-generators", new GameVariableList<>(GameVariableLocation.class, "Diamond generator locations, do 2 blocks above the ground"));
        addGameVariable("diamond-times", new GameVariableList<>(GameVariableInt.class, "The amount of time to spawn a diamond for each upgrade of the diamond generator")); // [30, 25, 20]
        addGameVariable("diamond-upgrade-time", new GameVariableInt("The amount of seconds until the diamond generator upgrades"), 300);
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

        generatorTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            int diamondUpgradeTime = (int) getVariable("diamond-upgrade-time");
            List<Integer> diamondIntervals = (List<Integer>) getVariable("diamond-times");
            secondsElapsed++;
            if (secondsElapsed >= diamondUpgradeTime) {
                secondsElapsed = 0;
                if (diamondIntervals.size() > diamondGeneratorLevel) {
                    diamondGeneratorLevel++;
                    GameUtils.messagePlayerList(state.keySet(), GameUtils.createColorString("&#b9f2ffDiamond generators are now at &e&lLevel " + diamondGeneratorLevel));
                }
            }
            if (secondsElapsed % diamondIntervals.get(diamondGeneratorLevel - 1) == 0) {
                List<Location> diamondGenerators = (List<Location>) getVariable("diamond-generators");
                for (Location dgLocation: diamondGenerators) {
                    Item diamond = Objects.requireNonNull(dgLocation.getWorld()).dropItem(dgLocation, new ItemStack(Material.DIAMOND));
                    diamond.setVelocity(new Vector(0, 0, 0));
                }
            }
        }, 0L, 20L);
        super.onPvPGameStart(list);
    }

    @Override
    public void onPlayerLeave(Player player) {
        if (state.size() <= 1) finishGame();
    }

    @Override
    protected BedwarsState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof BedwarsState)) return null;
        return (BedwarsState) state.get(p);
    }

    @Override
    public void onGameFinish() {
        Bukkit.getScheduler().cancelTask(generatorTask);
        generatorTask = -1;
        diamondGeneratorLevel = 1;
        secondsElapsed = 0;
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
        super.onPvPPlayerDeath(deathEvent);
    }
}
