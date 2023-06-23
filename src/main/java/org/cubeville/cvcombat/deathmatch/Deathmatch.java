package org.cubeville.cvcombat.deathmatch;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;

import java.util.*;
import java.util.stream.Collectors;

public class Deathmatch extends PvPTeamSelectorGame {

    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;


    public Deathmatch(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("tdm-max-score", new GameVariableInt("The max score a team can get before they win"), 20);
        addGameVariable("tdm-duration", new GameVariableInt("The max amount of time a game lasts (in minutes)"), 10);
    }

    public PvPGameOptions getOptions() {
        return new PvPGameOptions();
    }

    @Override
    public List<Integer[]> getSortedTeams() {
        return teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
    }

    @Override
    protected DeathmatchState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof DeathmatchState)) return null;
        return (DeathmatchState) state.get(p);
    }

    @Override
    public void onPlayerLeave(Player p) {
        super.onPvPPlayerLeave(p);
        DeathmatchState ds = getState(p);
        if (isLastOnTeam(p)) {
            teamScores.set(ds.team, new Integer[]{ds.team, -999});
        }
        if (state.size() <= 1 || teamScores.stream().filter(score -> score[1] != -999).count() <= 1) finishGame();
    }
    @Override
    public void onGameStart(List<Set<Player>> playerTeamMap) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = playerTeamMap.get(i);

            if (teamPlayers == null) {
                continue;
            }

            ChatColor chatColor = (ChatColor) team.get("chat-color");
            teamScores.add(new Integer[]{ i, 0 });

            for (Player player : teamPlayers) {
                state.put(player, new DeathmatchState(i));
                player.sendMessage(chatColor + "First to " + getVariable("tdm-max-score") + " points wins!");

            }
        }
        super.onPvPGameStart(playerTeamMap);
        startTime = System.currentTimeMillis();


        // add the initial spawn time to the duration
        long duration = ((((int) getVariable("tdm-duration")) * 60L) + ((int) getVariable("initial-spawn-time"))) * 1000L;
        scoreboardSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                updateDefaultScoreboard((int) currentTime, teamScores, "kills");
            } else {
                finishGame();
            }
        }, 0L, 20L);
    }

    @Override
    protected String getKitInventoryName() {
        return "§lDeathmatch on " + arena.getName();
    }

    @Override
    public void onGameFinish() {
        super.onPvPGameFinish();
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        getSortedTeams().forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            if (pair[1] == -999) {
                sendMessageToArena(chatColor + teamName + "§f: §cLeft Game");
            } else {
                sendMessageToArena(chatColor + teamName + "§f: " + pair[1] + " kills");
            }
        });
        state.keySet().forEach(this::playerPostGame);
        teamScores.clear();
    }

    private void playerPostGame(Player p) {
        sendStatistics(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private void sendStatistics(Player player) {
        DeathmatchState ds = getState(player);
        player.sendMessage("§7Kills: §f" + ds.kills);
        player.sendMessage("§7Deaths: §f" + ds.deaths);
        if (ds.kills == 0) {
            player.sendMessage("§7K/D Ratio: §f0.00");
        } else if (ds.deaths == 0) {
            player.sendMessage("§7K/D Ratio: §f" + ds.kills + ".00");
        } else {
            player.sendMessage("§7K/D Ratio: §f" + String.format("%.2f", ((float) ds.kills / (float) ds.deaths)));
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        super.onPvPPlayerDamage(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        DeathmatchState killerState = getState(killer);
        Player hit = e.getEntity();
        DeathmatchState hitState = getState(hit);

        // check to make sure the person who died is in the pvp game
        if (hitState == null) return;

        super.onPvPPlayerDeath(e);

        if (killerState == null || killer == null) {
            // if we can't find a killer, subtract 1 from the score of the team
            teamScores.set(hitState.team, new Integer[]{ hitState.team, teamScores.get(hitState.team)[1] - 1 });
        } else {
            // if we have a killer, add one to their team's total
            teamScores.set(killerState.team, new Integer[]{ killerState.team, teamScores.get(killerState.team)[1] + 1 });

            // we only need to check to end the game if there is a killer
            int maxScore = (int) getVariable("tdm-max-score");
            if (teamScores.get(killerState.team)[1] >= maxScore) {
                finishGame();
                // can't teleport someone to the exit on the same tick as they die -- so we need to do it manually here
                Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> teleportOut(hit), 1);
                return;
            }
        }
        updateDefaultScoreboard((int) currentTime, teamScores, "kills");
    }
}
