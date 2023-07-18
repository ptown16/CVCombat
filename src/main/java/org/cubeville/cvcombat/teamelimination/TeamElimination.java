package org.cubeville.cvcombat.teamelimination;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.GameVariableDouble;
import org.cubeville.cvgames.vartypes.GameVariableInt;

import java.util.*;
import java.util.stream.Collectors;

public class TeamElimination extends PvPTeamSelectorGame {
    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private final ArrayList<Integer[]> teamOvrScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;
    protected int revealSecondUpdater;

    public TeamElimination(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("teamelim-max-score", new GameVariableInt("The required amount of rounds won for a team to win."), 2);
        addGameVariable("teamelim-kill-health", new GameVariableDouble("The amount of health gained from getting a kill. 2 = 1 heart."), 10.0);
        addGameVariable("teamelim-reveal-time", new GameVariableInt("Time in minutes from start of round for all players to be glowing."), 4);
    }

    @Override
    public PvPGameOptions getOptions() {
        return new PvPGameOptions();
    }

    @Override
    public List<Integer[]> getSortedTeams() {
        return teamScores.stream().sorted(Comparator.comparingInt((Integer[] o) -> -1 * teamOvrScores.get(o[0])[1]).thenComparingInt(o -> -1 * o[1])).collect(Collectors.toList());
    }

    @Override
    public void onGameStart(List<Set<Player>> list) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = list.get(i);

            if (teamPlayers == null) {
                continue;
            }

            ChatColor chatColor = (ChatColor) team.get("chat-color");
            teamScores.add(new Integer[]{ i, 0 });
            teamOvrScores.add(new Integer[]{ i, 0 });

            for (Player player : teamPlayers) {
                state.put(player, new TeamEliminationState(i));
                teamScores.set(i, new Integer[]{ i, teamScores.get(i)[1] + 1 });
                player.sendMessage(chatColor + "Last team standing in " + getVariable("tdm-max-score") + " rounds wins!");
            }
        }
        super.onPvPGameStart(list);
        displayScoreboard();

        startRevealUpdater();
    }

    private void startRevealUpdater() {
        startTime = System.currentTimeMillis();
        // add the initial spawn time to the duration
        long duration = ((((int) getVariable("teamelim-reveal-time")) * 60L) + ((int) getVariable("initial-spawn-time"))) * 1000L;
        revealSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                displayScoreboard();
            } else {
                for (Player player : state.keySet()) {
                    player.setGlowing(true);
                }
                sendMessageToArena("§aAll players have been revealed!");
                sendTitleToArena("§aAll players have been revealed!", "", 10, 40, 10);
                Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> {
                    Bukkit.getScheduler().cancelTask(revealSecondUpdater);
                }, 1);
            }
        }, 0L, 20L);
    }

    @Override
    protected String getKitInventoryName() {
        return "§lElimination on " + arena.getName();
    }

    private void startRound() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> {
            for (int i = 0; i < teamScores.size(); i++) {
                teamScores.set(i, new Integer[]{ i, 0 });
                for (Player player : teamPlayers.get(i)) {
                    getState(player).isAlive = true;
                    removeSpectator(player);
                    teamScores.set(i, new Integer[]{ i, teamScores.get(i)[1] + 1 });
                    Location tpLoc = (Location) getVariable("spectator-spawn");
                    if (!tpLoc.getChunk().isLoaded()) {
                        tpLoc.getChunk().load();
                    }
                    //    player.sendMessage(chatColor + "First to " + getVariable("tdm-max-score") + " points wins!");
                    int initialSpawnTime = usesDefaultKits() ? (int) getVariable("initial-spawn-time") : 0;
                    startPlayerRespawn(player, initialSpawnTime);
                    // force open the inventory, but only on the first spawn in
                    if (usesDefaultKits()) {
                        player.openInventory(generateKitInventory(player));
                    }
                }
            }
            displayScoreboard();
            startRevealUpdater();
        }, 3 * 20L);
    }

    @Override
    public void onPlayerLeave(Player player) {
        TeamEliminationState es = getState(player);
        if (getState(player).isAlive) {
            teamScores.set(es.team, new Integer[]{es.team, teamScores.get(es.team)[1] - 1});
        }
        if (isLastOnTeam(player)) {
            teamScores.set(es.team, new Integer[]{es.team, -999});
        }
        removeSpectator(player);
        player.setGlowing(false);
        super.onPvPPlayerLeave(player);
        checkRoundEnd();
        if (state.size() <= 1) {
            finishGame();
        }
    }

    @Override
    protected TeamEliminationState getState(Player player) {
        return (TeamEliminationState) state.get(player);
    }

    @Override
    public void onGameFinish() {
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        getSortedTeams().forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            int ovrScore = teamOvrScores.get(pair[0])[1];
            if (pair[1] == -999) {
                sendMessageToArena(chatColor + teamName + "§f: §cLeft Game");
            } else {
                sendMessageToArena(chatColor + teamName + "§f: §a§l" + ovrScore + (ovrScore == 1 ? "§a round" : "§a rounds"));
            }
            teamPlayers.get(pair[0]).stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).getSortingValue())).forEach(p -> {
                String message = "§f- " + chatColor + p.getDisplayName() + " §a🗡§f";
                TeamEliminationState es = getState(p);
                message = message + es.kills + " §c❌§f";
                message = message + es.deaths + " §e⚖§f";
                if (es.kills == 0) {
                    message = message + "0.00";
                } else if (es.deaths == 0) {
                    message = message + es.kills + ".00";
                } else {
                    message = message + String.format("%.2f", ((float) es.kills / (float) es.deaths));
                }
                sendMessageToArena(message);
                p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
            });
        });
        super.onPvPGameFinish();
        teamScores.clear();
        teamOvrScores.clear();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        super.onPvPPlayerDamage(e);
    }

    private int aliveTeamCount() {
        int aliveTeams = 0;
        for (int i = 0; i < teams.size(); i++) {
            if (teamScores.get(i)[1] > 0) {
                aliveTeams++;
            }
        }
        return aliveTeams;
    }

    private boolean checkRoundEnd() {
        if (aliveTeamCount() < 2) {
            finishRound();
            return true;
        }
        return false;
    }

    private void finishRound() {
        Bukkit.getScheduler().cancelTask(revealSecondUpdater);
        revealSecondUpdater = -1;
        for (Player player : state.keySet()) {
            player.setGlowing(false);
        }
        int roundWinner = -1;
        for (int i = 0; i < teamScores.size(); i++) {
            if (teamScores.get(i)[1] > 0) {
                roundWinner = i;
                break;
            }
        }
        if (roundWinner == -1) {
            sendMessageToArena("§lAn error has occured: no team won.");
            Bukkit.getLogger().info("CVCombat Error! No team won in a round of a Team Elimination match! Scores:");
            for (int i = 0; i < teamScores.size(); i++) {
                Bukkit.getLogger().info("Team " + i + ": " + teamScores.get(i)[1]);
            }
            finishGame();
            return;
        }
        String teamName = (String) teams.get(roundWinner).get("name");
        ChatColor chatColor = (ChatColor) teams.get(roundWinner).get("chat-color");
        sendMessageToArena(chatColor + teamName + chatColor + " won the round!");
        sendTitleToArena(chatColor + teamName + chatColor + " won the round!", "", 10, 40, 10);
        teamOvrScores.set(roundWinner, new Integer[]{ roundWinner, teamOvrScores.get(roundWinner)[1] + 1 });
        if (teamOvrScores.get(roundWinner)[1] >= (Integer) getVariable("teamelim-max-score")) {
            finishGame();
        } else {
            startRound();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        TeamEliminationState killerState = getState(killer);
        Player hit = e.getEntity();
        TeamEliminationState hitState = getState(hit);

        // check to make sure the person who died is in the pvp game
        if (hitState == null) return;

        super.onPvPPlayerDeath(e, false);

        if (killerState != null && killer != null) {
            killer.setHealth(Math.min(killer.getHealth() + (Double) getVariable("teamelim-kill-health"), killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            killer.sendMessage("§a+" + getVariable("teamelim-kill-health") + " HP");
        }

        teamScores.set(hitState.team, new Integer[]{ hitState.team, teamScores.get(hitState.team)[1] - 1 });
        getState(hit).isAlive = false;
        displayScoreboard();
        healPlayer(hit);
        hit.setGlowing(false);
        boolean roundEnded = checkRoundEnd();

        if (roundEnded) {
            // can't teleport someone to the exit on the same tick as they die -- so we need to do it manually here
            if (!isRunningGame) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> {
                    teleportOut(hit);
                }, 1);
                return;
            }
        }

        healPlayer(hit);
        hit.getInventory().clear();
        addSpectator(hit);
        Bukkit.getScheduler().scheduleSyncDelayedTask(CVGames.getInstance(), () -> {
            // if the game is still going in a second, give them the spectator inventory
            if (this.isRunningGame && !getState(hit).isAlive) {
                arena.getQueue().setSpectatorInventory(hit.getInventory());
            }
        }, 20L);
    }

    private void displayScoreboard() {
        List<String> scoreboardLines = new ArrayList<>();
        List<Integer[]> sortedTeams = getSortedTeams();
        Object[] time = new Object[]{currentTime / '\uea60', currentTime / 1000 % 60};
        scoreboardLines.add("§bTime Until Player Reveal: §f" + String.format("%d:%02d", time));
        for (Integer[] team : sortedTeams) {
            Integer teamNr = team[0];
            Integer teamScore = team[1];
            Integer teamOvrScore = teamOvrScores.get(teamNr)[1];
            Map<String, Object> teamData = teams.get(teamNr);
            ChatColor color = (ChatColor) teamData.get("chat-color");
            String name = (String) teamData.get("name");
            String message = "";
            for (int i=1; i<=(Integer) getVariable("teamelim-max-score"); i++) {
                if (teamOvrScore >= i) {
                    message = message + color + "§l\uD83C\uDFC6";
                }
                else {
                    message = message + "§7§l\uD83C\uDFC6";
                }
            }
            message = message + " " + color + name + " §f- §a" + teamScore + "\uD83D\uDC64";
            scoreboardLines.add(message);
            for (Player player : teamPlayers.get(teamNr)) {
                String pMessage = "";
                if (getState(player).isAlive) {
                    pMessage = "        " + color + player.getDisplayName();
                } else {
                    pMessage = "        §7❌ " + player.getDisplayName();
                }
                scoreboardLines.add(pMessage);
            }
        }
        Scoreboard scoreboard = GameUtils.createScoreboard(arena, "§e§lTeam Elimination", scoreboardLines);
        sendScoreboardToArena(scoreboard);
    }
    private void sendTitleToArena(String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
        arena.getQueue().getPlayerSet().forEach(p -> p.sendTitle(title, subtitle, fadeIn, stay, fadeOut));
    }
}
