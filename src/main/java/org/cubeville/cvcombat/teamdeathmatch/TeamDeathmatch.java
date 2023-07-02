package org.cubeville.cvcombat.teamdeathmatch;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.vartypes.*;

import java.util.*;
import java.util.stream.Collectors;

public class TeamDeathmatch extends PvPTeamSelectorGame {

    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;
    protected int scoreboardSecondUpdater;



    public TeamDeathmatch(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("tdm-max-score", new GameVariableInt("The max score a team can get before they win"), 20);
        addGameVariable("dm-duration", new GameVariableInt("The max amount of time a game lasts (in minutes)"), 10);
    }

    public PvPGameOptions getOptions() {
        return new PvPGameOptions();
    }

    @Override
    public List<Integer[]> getSortedTeams() {
        return teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
    }

    @Override
    protected TeamDeathmatchState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof TeamDeathmatchState)) return null;
        return (TeamDeathmatchState) state.get(p);
    }

    @Override
    public void onPlayerLeave(Player p) {
        TeamDeathmatchState ds = getState(p);
        if (isLastOnTeam(p)) {
            teamScores.set(ds.team, new Integer[]{ds.team, -999});
        }
        super.onPvPPlayerLeave(p);
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
                state.put(player, new TeamDeathmatchState(i));
                player.sendMessage(chatColor + "First to " + getVariable("tdm-max-score") + " points wins!");

            }
        }
        super.onPvPGameStart(playerTeamMap);
        startTime = System.currentTimeMillis();


        // add the initial spawn time to the duration
        long duration = ((((int) getVariable("dm-duration")) * 60L) + ((int) getVariable("initial-spawn-time"))) * 1000L;
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
        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        getSortedTeams().forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            if (pair[1] == -999) {
                sendMessageToArena(chatColor + teamName + "§f: §cLeft Game");
            } else {
                sendMessageToArena(chatColor + teamName + "§f: §a§l" + pair[1] + " §akills");
            }
            teamPlayers.get(pair[0]).stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).getSortingValue())).forEach(p -> {
                String message = "§f- " + chatColor + p.getDisplayName() + " §a🗡§f";
                TeamDeathmatchState ds = getState(p);
                message = message + ds.kills + " §c❌§f";
                message = message + ds.deaths + " §e⚖§f";
                if (ds.kills == 0) {
                    message = message + "0.00";
                } else if (ds.deaths == 0) {
                    message = message + ds.kills + ".00";
                } else {
                    message = message + String.format("%.2f", ((float) ds.kills / (float) ds.deaths));
                }
                sendMessageToArena(message);
                p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
            });
        });
        super.onPvPGameFinish();
        teamScores.clear();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        super.onPvPPlayerDamage(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        TeamDeathmatchState killerState = getState(killer);
        Player hit = e.getEntity();
        TeamDeathmatchState hitState = getState(hit);

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
