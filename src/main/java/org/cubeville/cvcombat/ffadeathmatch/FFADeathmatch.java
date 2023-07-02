package org.cubeville.cvcombat.ffadeathmatch;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPFFAGame;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvgames.vartypes.GameVariableInt;

import java.util.*;
import java.util.stream.Collectors;

public class FFADeathmatch extends PvPFFAGame {

    private long startTime = 0;
    private long currentTime;
    protected int scoreboardSecondUpdater;

    public FFADeathmatch(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("ffadm-max-score", new GameVariableInt("The max score a player can get before they win"), 20);
        addGameVariable("dm-duration", new GameVariableInt("The max amount of time a game lasts (in minutes)"), 10);
    }

    public PvPGameOptions getOptions() {
        return new PvPGameOptions();
    }

    @Override
    public List<Player> getSortedPlayers() {
        return state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).kills)).collect(Collectors.toList());
    }

    @Override
    protected FFADeathmatchState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof FFADeathmatchState)) return null;
        return (FFADeathmatchState) state.get(p);
    }

    @Override
    public void onPlayerLeave(Player p) {
        super.onPlayerLeave(p);
        if (state.size() <= 1) finishGame();
    }
    @Override
    public void onGameStart(Set<Player> players) {
        ChatColor chatColor = (ChatColor) getVariable("ffa-chat-color");
        for (Player player : players) {
            state.put(player, new FFADeathmatchState());
            player.sendMessage(chatColor + "First to " + getVariable("ffadm-max-score") + " points wins!");

        }
        super.onGameStart(players);
        startTime = System.currentTimeMillis();


        // add the initial spawn time to the duration
        long duration = ((((int) getVariable("dm-duration")) * 60L) + ((int) getVariable("initial-spawn-time"))) * 1000L;
        scoreboardSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                updateDefaultScoreboard((int) currentTime, "score", false);
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
        List<Player> sortedPlayers  = state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).kills)).collect(Collectors.toList());

        sortedPlayers.forEach(player -> {
            FFADeathmatchState ds = getState(player);
            ChatColor chatColor = (ChatColor) getVariable("ffa-chat-color");
            String message = "§f- " + chatColor + player.getDisplayName() + " §a🗡§f";
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
            player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        });

        super.onGameFinish();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        super.onPlayerDamage(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        FFADeathmatchState killerState = getState(killer);
        Player hit = e.getEntity();
        FFADeathmatchState hitState = getState(hit);

        // check to make sure the person who died is in the pvp game
        if (hitState == null) return;

        super.onPlayerDeath(e);

        if (killerState == null || killer == null) {
            // if we can't find a killer, subtract 1 from their score
            hitState.score -= 1;
        } else {
            // if we have a killer, add one to their total
            killerState.score += 1;

            // we only need to check to end the game if there is a killer
            int maxScore = (int) getVariable("ffadm-max-score");
            if (killerState.score >= maxScore) {
                finishGame();
                // can't teleport someone to the exit on the same tick as they die -- so we need to do it manually here
                Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> teleportOut(hit), 1);
                return;
            }
        }
        updateDefaultScoreboard((int) currentTime, "score", false);
    }
}
