package org.cubeville.cvcombat.capturetheflag;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPPlayerState;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;

import java.util.*;
import java.util.stream.Collectors;

public class CaptureTheFlag extends PvPTeamSelectorGame {

    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private final ArrayList<Boolean> teamFlags = new ArrayList<Boolean>();
    private final ArrayList<Block> teamFlagBlocks = new ArrayList<Block>();
    private final ArrayList<Integer> flagsReturning = new ArrayList<Integer>();
    protected int flagCaptureChecker;

    public CaptureTheFlag(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("ctf-max-score", new GameVariableInt("The required amount of flags captured for a team to win."), 3);
    }

    @Override
    public PvPGameOptions getOptions() {
        return new PvPGameOptions();
    }

    @Override
    public List<Integer[]> getSortedTeams() {
        return teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
    }

    @Override
    public void addTeamVariables(HashMap<String, GameVariable> teamVariables) {
        teamVariables.put("flag-block", new GameVariableBlock("The location of the block the flag spawns on in CTF"));
        teamVariables.put("flag-data", new GameVariableBlockData("The data for the flag in CTF"));
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
            placeFlag(i);
            teamFlags.add(true);
            teamFlagBlocks.add((Block) team.get("flag-block"));

            for (Player player : teamPlayers) {
                state.put(player, new CaptureTheFlagState(i));
                player.sendMessage(chatColor + "First to " + getVariable("ctf-max-score") + " flags captured wins!");
            }
        }
        super.onPvPGameStart(playerTeamMap);

        flagCaptureChecker = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            for (Player player : state.keySet()) {
                CaptureTheFlagState cs = getState(player);
                if (cs.heldFlag == -1) return;
                if (player.getLocation().distance(((Block) teams.get(cs.team).get("flag-block")).getLocation()) <= 5) {
                    if (!teamFlags.get(cs.team)) {
                        player.sendTitle("§cUnable to capture!", "§fYour team doesn't have your flag!", 0, 20, 5);
                        return;
                    }
                    HashMap<String, Object> team = teams.get(cs.team);
                    ChatColor color = (ChatColor) team.get("chat-color");
                    String name = (String) team.get("name");
                    teamScores.set(cs.team, new Integer[]{ cs.team, teamScores.get(cs.team)[1] + 1 });
                    placeFlag(cs.heldFlag);
                    teamFlags.set(cs.heldFlag, true);
                    playFlagSound(cs.team, cs.heldFlag);
                    getState(player).flags++;
                    cs.heldFlag = -1;
                    player.setGlowing(false);
                    sendTitleToArena(color + name + " flag has been captured!", "§fThey have §a" + teamScores.get(cs.team)[1] + "§f flags captured!", 10, 60, 10);
                    displayScoreboard();
                    if (teamScores.get(cs.team)[1] >= (Integer) getVariable("ctf-max-score")) {
                        Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), this::finishGame, 1L);
                    }
                }
            }
        }, 0L, 5L);
        displayScoreboard();
    }

    @Override
    public void onPlayerLeave(Player p) {
        CaptureTheFlagState cs = getState(p);
        if (isLastOnTeam(p)) {
            teamScores.set(cs.team, new Integer[]{cs.team, -999});
        }
        if (cs.heldFlag != -1) {
            p.setGlowing(false);
            playFlagSound(cs.heldFlag, cs.team);
            returnFlag(cs.heldFlag);
        }
        super.onPvPPlayerLeave(p);
        if (state.size() <= 1 || teamScores.stream().filter(score -> score[1] != -999).count() <= 1) finishGame();
    }

    @Override
    protected String getKitInventoryName() {
        return "§lCTF on " + arena.getName();
    }

    @Override
    protected CaptureTheFlagState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof CaptureTheFlagState)) return null;
        return (CaptureTheFlagState) state.get(p);
    }

    @Override
    public void onGameFinish() {
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        getSortedTeams().forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            if (pair[1] == -999) {
                sendMessageToArena(chatColor + teamName + "§f: §cLeft Game");
            } else {
                sendMessageToArena(chatColor + teamName + "§f: §a§l" + pair[1] + " §flags");
            }
            removeFlag(pair[0]);
            teamPlayers.get(pair[0]).stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).getSortingValue())).forEach(p -> {
                String message = "§f- " + chatColor + p.getDisplayName() + " §b⚐§f";
                CaptureTheFlagState cs = getState(p);
                message = message + cs.flags + " §a🗡§f";
                message = message + cs.kills + " §c❌§f";
                message = message + cs.deaths + " §e⚖§f";
                if (cs.kills == 0) {
                    message = message + "0.00";
                } else if (cs.deaths == 0) {
                    message = message + cs.kills + ".00";
                } else {
                    message = message + String.format("%.2f", ((float) cs.kills / (float) cs.deaths));
                }
                sendMessageToArena(message);
                p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
                p.setGlowing(false);
            });
        });
        super.onPvPGameFinish();
        teamScores.clear();
        teamFlags.clear();
        teamFlagBlocks.clear();
        for (Integer i : flagsReturning) {
            Bukkit.getScheduler().cancelTask(i);
        }
        flagsReturning.clear();
        Bukkit.getScheduler().cancelTask(flagCaptureChecker);
    }

    @EventHandler
    public void onPlayerClickBlock(PlayerInteractEvent event) {
        if (getState(event.getPlayer()) == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!teamFlagBlocks.contains(event.getClickedBlock())) return;
        Player player = event.getPlayer();
        CaptureTheFlagState cs = getState(player);
        if (cs.respawnTimer != -1 || cs.heldFlag != - 1) return;
        Integer flag = teamFlagBlocks.indexOf(event.getClickedBlock());
        if (!teamFlags.get(flag)) return;
        if (flag == cs.team) {
            player.sendMessage("§cYou may not take your own team's flag!");
            return;
        }
        player.setGlowing(true);
        removeFlag(flag);
        cs.heldFlag = flag;
        teamFlags.set(flag, false);
        HashMap<String, Object> team = teams.get(flag);
        ChatColor color = (ChatColor) team.get("chat-color");
        String name = (String) team.get("name");
        HashMap<String, Object> stealTeam = teams.get(cs.team);
        ChatColor stealColor = (ChatColor) stealTeam.get("chat-color");
        sendTitleToArena(color + name + " flag has been stolen!", "§fTaken by " + stealColor + player.getDisplayName(), 10, 60, 10);
        playFlagSound(cs.team, flag);
        displayScoreboard();
    }

    private void placeFlag(Integer i) {
        HashMap<String, Object> team = teams.get(i);
        Block loc = (Block) team.get("flag-block");
        BlockData data = (BlockData) team.get("flag-data");
        loc.setBlockData(data);
    }

    private void removeFlag(Integer i) {
        HashMap<String, Object> team = teams.get(i);
        Block loc = (Block) team.get("flag-block");
        loc.setType(Material.AIR);
    }

    private void returnFlag(Integer i) {
        HashMap<String, Object> team = teams.get(i);
        ChatColor color = (ChatColor) team.get("chat-color");
        String name = (String) team.get("name");
        sendTitleToArena(color + name + " flag has been dropped!", "§fReturning in §a10s", 10, 60, 10);
        flagsReturning.add(Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> {
            placeFlag(i);
            teamFlags.set(i, true);
            sendTitleToArena(color + name + " flag has been returned!", "", 10, 60, 10);
            displayScoreboard();
        }, 10L * 20L));
    }

    private void playFlagSound(Integer winner, Integer loser) {
        for (Player p : teamPlayers.get(winner)) {
            p.playSound(p, Sound.BLOCK_BEACON_DEACTIVATE, 10f, 0.8f);
        }
        for (Player p : teamPlayers.get(loser)) {
            p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 10f, 2f);
        }
    }

    private void sendTitleToArena(String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
        arena.getQueue().getPlayerSet().forEach(p -> p.sendTitle(title, subtitle, fadeIn, stay, fadeOut));
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        super.onPvPPlayerDamage(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        CaptureTheFlagState killerState = getState(killer);
        Player hit = e.getEntity();
        CaptureTheFlagState hitState = getState(hit);

        // check to make sure the person who died is in the pvp game
        if (hitState == null) return;

        super.onPvPPlayerDeath(e);

        if (hitState.heldFlag != -1) {
            Integer flag = hitState.heldFlag;
            hitState.heldFlag = -1;
            playFlagSound(flag, hitState.team);
            returnFlag(flag);
            hit.setGlowing(false);
        }
    }

    private void displayScoreboard() {
        List<String> scoreboardLines = new ArrayList<>();
        List<Integer[]> sortedTeams = getSortedTeams();
        for (Integer[] team : sortedTeams) {
            Integer teamNr = team[0];
            Integer teamScore = team[1];
            Map<String, Object> teamData = teams.get(teamNr);
            ChatColor color = (ChatColor) teamData.get("chat-color");
            String name = (String) teamData.get("name");
            String message = "§f(";
            if (teamFlags.get(teamNr)) {
                message = message + color + "⚑§f) ";
            } else {
                message = message + "§7⚐§f) ";
            }
            message = message + color + name + " §f- ";
            for (int i=1; i<=(Integer) getVariable("ctf-max-score"); i++) {
                if (teamScore >= i) {
                    message = message + color + "⚑";
                }
                else {
                    message = message + "§7⚐";
                }
            }
                scoreboardLines.add(message);
        }
        Scoreboard scoreboard = GameUtils.createScoreboard(arena, "§c§lCapture The Flag", scoreboardLines);
        sendScoreboardToArena(scoreboard);
    }
}
