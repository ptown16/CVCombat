package org.cubeville.cvcombat.deathmatch;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.DisplaySlot;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.models.PvPGameOptions;
import org.cubeville.cvcombat.models.PvPTeamSelectorGame;
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;

import java.util.*;
import java.util.stream.Collectors;

public class Deathmatch extends PvPTeamSelectorGame {

    private String error;
    private int scoreboardSecondUpdater;
    private List<HashMap<String, Object>> teams;
    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;
    private boolean hasSpawned = false;
    private ArrayList<String> indexToLoadoutName = new ArrayList<>();
    private ArrayList<Integer> teamIndexToTpIndex = new ArrayList<>();

    private final ItemStack KIT_SELECTION_ITEM = GameUtils.customHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmRkMTZhOTU5ZGQ5MmE3MzU4MWJjNjE2NGQzZjgxZDQ2MjQ0YmUyOTdkMjVmYmUwNmNiNTA5NTQ4NTY1NWZkNCJ9fX0=", "§d§lSelect Kit §7§o(Right Click)");
    private final Random random = new Random();


    public Deathmatch(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("tdm-max-score", new GameVariableInt("The max score a team can get before they win"), 20);
        addGameVariable("tdm-duration", new GameVariableInt("The max amount of time a game lasts (in minutes)"), 10);
    }

    public PvPGameOptions getOptions() {
        PvPGameOptions options = new PvPGameOptions();
        return options;
    }

    @Override
    protected DeathmatchState getState(Player p) {
        if (p == null || state.get(p) == null || !(state.get(p) instanceof DeathmatchState)) return null;
        return (DeathmatchState) state.get(p);
    }

    @Override
    public int getPlayerTeamIndex(Player player) {
        return Objects.requireNonNull(getState(player)).team;
    }

    @Override
    public void onPlayerLeave(Player p) {
        DeathmatchState ds = getState(p);
        if (ds == null) return;
        Bukkit.getScheduler().cancelTask(ds.respawnTimer);
        healPlayer(p);
        GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
            "arena", arena.getName(),
            "game", "deathmatch",
            "team", (String) teams.get(ds.team).get("name"),
            "player", p.getUniqueId().toString(),
            "result", "leave"
        ));
        ds.respawnTimer = -1;
        if (isLastOnTeam(p)) {
            teamScores.set(ds.team, new Integer[]{ds.team, -999});
        }
        state.remove(p);
        if (state.size() <= 1 || teamScores.stream().filter(score -> score[1] != -999).count() <= 1) finishGame();
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private boolean isLastOnTeam(Player p) {
        DeathmatchState ds = getState(p);
        if (ds == null) return false;
        for (Player player : state.keySet()) {
            if (!player.equals(p) && ((DeathmatchState) state.get(player)).team == ds.team) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onGameStart(List<Set<Player>> playerTeamMap) {
        super.onPvPGameStart(playerTeamMap);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getKitInventoryName())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        DeathmatchState clickerState = getState(clicker);
        if (clickerState == null) return;

        // player must be currently selecting a kit, cancel all clicks and handle from there
        event.setCancelled(true);

        // don't handle a click unless it is on one of the kits
        if (event.getSlot() >= indexToLoadoutName.size() || event.getSlot() < 0) return;
        clickerState.selectedKit = event.getSlot();

        // apply the loadout to the player, so they can see what the loadout contains
        applyLoadoutFromState(clicker, clickerState);
        clicker.getInventory().setItem(8, KIT_SELECTION_ITEM);
        event.getWhoClicked().closeInventory();
    }

    @Override
    public void onGameFinish() {
        for (Player player : state.keySet()) {
            DeathmatchState ds = (DeathmatchState) state.get(player);
            if (state.containsKey(player) && ds.respawnTimer != -1) {
                Bukkit.getScheduler().cancelTask(ds.respawnTimer);
                ds.respawnTimer = -1;
            }
            healPlayer(player);
        }

        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;

        if (error != null) {
            GameUtils.messagePlayerList(state.keySet(), "§c§lERROR: §c" + error);
        } else if (teams.size() > 1) {
            finishTeamGame();
        } else {
            finishFFAGame();
        }
        error = null;
        teamScores.clear();
        indexToLoadoutName.clear();
        teamIndexToTpIndex.clear();
    }

    private void finishFFAGame() {
        List<Player> sortedPlayers  = state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).kills)).collect(Collectors.toList());

        ChatColor chatColor = (ChatColor) teams.get(0).get("chat-color");

        if (sortedPlayers.size() > 1 && getState(sortedPlayers.get(0)).kills == getState(sortedPlayers.get(1)).kills) {
            state.keySet().forEach(p -> {
                StringBuilder output = new StringBuilder();
                output.append(chatColor);
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    if (i == (sortedPlayers.size() - 1) || getState(sortedPlayers.get(i)).kills != getState(sortedPlayers.get(i + 1)).kills) {
                        output.append(" and ").append(sortedPlayers.get(i).getDisplayName());
                        break;
                    } else if (i == 0) {
                        output.append(sortedPlayers.get(i).getDisplayName());
                    } else {
                        output.append(", ").append(sortedPlayers.get(i).getDisplayName());
                    }
                }
                output.append(" win the game!");
                p.sendMessage(output.toString());
            });
        } else {
            state.keySet().forEach(p -> {
                p.sendMessage(chatColor + sortedPlayers.get(0).getDisplayName() + " has won the game!");
            });
        }

        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        sortedPlayers.forEach(player -> {
            sendMessageToArena(chatColor + player.getDisplayName() + "§f: " + getState(player).kills + " kills");
        });
        state.keySet().forEach(this::playerPostGame);
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


    private void finishTeamGame() {
        List<Integer[]> sortedTeams = teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
        if (Objects.equals(sortedTeams.get(0)[1], sortedTeams.get(1)[1])) {
            sendMessageToArena("§f§lTie Game!");
            sendTeamResultMetrics(-1);
        } else {
            int winningTeamIndex = sortedTeams.get(0)[0];
            String teamName = (String) teams.get(winningTeamIndex).get("name");
            ChatColor chatColor = (ChatColor) teams.get(winningTeamIndex).get("chat-color");
            sendMessageToArena(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
            sendTeamResultMetrics(winningTeamIndex);
        }
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        sortedTeams.forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            if (pair[1] == -999) {
                sendMessageToArena(chatColor + teamName + "§f: §cLeft Game");
            } else {
                sendMessageToArena(chatColor + teamName + "§f: " + pair[1] + " kills");
            }
        });
        state.keySet().forEach(this::playerPostGame);
    }

    private void sendTeamResultMetrics(int winningTeamIndex) {
        for (int i = 0; i < teams.size(); i++) {
            String result = i == winningTeamIndex ? "win" : "loss";
            GameUtils.sendMetricToCVStats("pvp_team_result", Map.of(
                "arena", arena.getName(),
                "game", "deathmatch",
                "team", (String) teams.get(i).get("name"),
                "result", winningTeamIndex == -1 ? "tie" : result
            ));
        }
        state.keySet().forEach(player -> {
            DeathmatchState pState = getState(player);
            String result = pState.team == winningTeamIndex ? "win" : "loss";
            GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
                "arena", arena.getName(),
                "game", "deathmatch",
                "team", (String) teams.get(pState.team).get("name"),
                "player", player.getUniqueId().toString(),
                "result", winningTeamIndex == -1 ? "tie" : result
            ));
        });
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        // is the player holding the kit selection item?
        if (e.getItem() == null) return;
        if (!e.getItem().isSimilar(KIT_SELECTION_ITEM)) return;

        // is the player in the game?
        if (getState(e.getPlayer()) == null) return;

        e.setCancelled(true);
        e.getPlayer().openInventory(generateKitInventory(e.getPlayer()));
    }


    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        // if there's friendly fire, we don't need to check any of this
        if ((Boolean) getVariable("friendly-fire")) return;

        // check if the hit is within the game
        if (!(e.getEntity() instanceof Player)) return;
        Player damager;
        if (e.getDamager() instanceof Player) {
            damager = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile arrow = (Projectile) e.getDamager();
            if (!(arrow.getShooter() instanceof Player)) return;
            damager = (Player) arrow.getShooter();
        } else {
            return;
        }
        DeathmatchState damagerState = getState(damager);
        Player hit = (Player) e.getEntity();
        DeathmatchState hitState = getState(hit);
        if (damagerState == null || hitState == null) return;

        // prevent friendly fire, if applicable
        if (teams.size() > 1 && !((Boolean) getVariable("friendly-fire")) && damagerState.team == hitState.team) {
            e.getDamager().sendMessage("§cDon't hit your teammates!");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        DeathmatchState killerState = getState(killer);
        Player hit = e.getEntity();
        DeathmatchState hitState = getState(hit);

        // check to make sure the person who died is in the pvp game
        if (hitState == null) return;

        // make sure the player drops nothing on death
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        hit.getInventory().clear();

        hitState.deaths += 1;
        ChatColor hitChatColor = (ChatColor) teams.get(hitState.team).get("chat-color");
        // grab a copy of the death message and set the server-wide death message to null
        String deathMessage = "§e" + e.getDeathMessage();
        e.setDeathMessage(null);
        deathMessage = deathMessage.replaceAll(hit.getDisplayName(), hitChatColor + hit.getDisplayName() + "§e");

        // remove all potion fx from the player who died
        for (PotionEffect effect : hit.getActivePotionEffects()) {
            hit.removePotionEffect(effect.getType());
        }

        GameUtils.sendMetricToCVStats("pvp_death", Map.of(
            "arena", arena.getName(),
            "game", "deathmatch",
            "kit", indexToLoadoutName.get(hitState.selectedKit),
            "player", hit.getUniqueId().toString()
        ));

        if (killerState == null || killer == null) {
            // if we can't find a killer, subtract 1 from the score of the team
            teamScores.set(hitState.team, new Integer[]{ hitState.team, teamScores.get(hitState.team)[1] - 1 });
            sendMessageToArena(deathMessage);
        } else {
            // if we have a killer, add one to their team's total
            killerState.kills += 1;
            teamScores.set(killerState.team, new Integer[]{ killerState.team, teamScores.get(killerState.team)[1] + 1 });
            GameUtils.sendMetricToCVStats("pvp_kill", Map.of(
                "arena", arena.getName(),
                "game", "deathmatch",
                "kit", indexToLoadoutName.get(killerState.selectedKit),
                "player", killer.getUniqueId().toString()
            ));

            ChatColor killerChatColor = (ChatColor) teams.get(killerState.team).get("chat-color");
            deathMessage = deathMessage.replaceAll(killer.getDisplayName(), killerChatColor + killer.getDisplayName() + "§e");

            sendMessageToArena(deathMessage);

            // we only need to check to end the game if there is a killer
            int maxScore = (int) getVariable("tdm-max-score");
            if (teams.size() > 1) {
                if (teamScores.get(killerState.team)[1] >= maxScore) {
                    finishGame();
                    // can't teleport someone to the exit on the same tick as they die -- so we need to do it manually here
                    Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> hit.teleport((Location) getVariable("exit")), 1);
                    return;
                }
            } else {
                if (killerState.kills >= maxScore) {
                    finishGame();
                    // can't teleport someone to the exit on the same tick as they die -- so we need to do it manually here
                    Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> hit.teleport((Location) getVariable("exit")), 1);
                    return;
                }
            }
        }

        startPlayerRespawn(hit, (int) getVariable("respawn-time"));
        updateDefaultScoreboard((int) currentTime, teamScores, "kills");
    }
}
