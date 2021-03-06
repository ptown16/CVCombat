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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.GameVariableKit;
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.GameVariableFlag;
import org.cubeville.cvgames.vartypes.GameVariableInt;
import org.cubeville.cvgames.vartypes.GameVariableList;
import org.cubeville.cvloadouts.CVLoadouts;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class Deathmatch extends TeamSelectorGame {

    private String error;
    private int scoreboardSecondUpdater;
    private List<HashMap<String, Object>> teams;
    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;
    private ArrayList<String> indexToLoadoutName = new ArrayList<>();
    private ArrayList<Integer> teamIndexToTpIndex = new ArrayList<>();


    public Deathmatch(String id) {
        super(id);
        addGameVariable("kits", new GameVariableList<>(GameVariableKit.class));
        addGameVariable("teams", new GameVariableList<>(DeathmatchTeam.class));
        addGameVariable("respawn-time", new GameVariableInt(), 10);
        addGameVariable("max-score", new GameVariableInt(), 20);
        addGameVariable("friendly-fire", new GameVariableFlag(), false);
        addGameVariable("duration", new GameVariableInt(), 10);
        setTeamVariable("teams");
    }

    @Nullable
    private DeathmatchState getState(Player p) {
        if (state.get(p) == null || !(state.get(p) instanceof DeathmatchState)) return null;
        return (DeathmatchState) state.get(p);
    }

    @Override
    public void onPlayerLeave(Player p) {
        DeathmatchState ds = getState(p);
        Bukkit.getScheduler().cancelTask(ds.respawnTimer);
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
        teams = (List<HashMap<String, Object>>) getVariable("teams");

        List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
        for (HashMap<String, Object> kit : kits) {
            System.out.println(kit.get("loadout"));
            indexToLoadoutName.add((String) kit.get("loadout"));
        }

        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = playerTeamMap.get(i);

            if (teamPlayers == null) { continue; }

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");

            teamScores.add(new Integer[]{ i, 0 });
            teamIndexToTpIndex.add(0);

            for (Player player : teamPlayers) {
                state.put(player, new DeathmatchState(i));

                Location tpLoc = (Location) team.get("kit-lobby");
                if (!tpLoc.getChunk().isLoaded()) {
                    tpLoc.getChunk().load();
                }
                player.teleport(tpLoc);

                if (teams.size() > 1) {
                    player.sendMessage(chatColor + "You are on ??l" + teamName + chatColor + "!");
                } else {
                    player.sendMessage(chatColor + "It's a free for all!");
                }
                player.sendMessage(chatColor + "First to " + getVariable("max-score") + " points wins!");
                forceKitSelection(player);
            }
        }
        startTime = System.currentTimeMillis();

        long duration = ((int) getVariable("duration")) * 60L * 1000L;
        scoreboardSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                updateScoreboard();
            } else {
                finishGame();
            }
        }, 0L, 20L);
    }

    private void forceKitSelection(Player p) {
        p.setHealth(20);
        DeathmatchState pState = getState(p);
        if (pState == null) return;
        p.teleport((Location) teams.get(pState.team).get("kit-lobby"));
        p.closeInventory();
        p.openInventory(generateKitInventory(p));
        pState.selectingKit = true;
        if (!pState.hasDied) return;
        pState.respawnTimer = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), new Runnable() {
            int respawnTime = ((int) getVariable("respawn-time"));
            boolean firstRun = true;
            @Override
            public void run() {
                if (respawnTime <= 0) {
                    spawnPlayerIntoGame(p);
                    Bukkit.getScheduler().cancelTask(pState.respawnTimer);
                    pState.respawnTimer = -1;
                } else if (respawnTime <= 3 || firstRun){
                    firstRun = false;
                    p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 3.0F, 1.4F);
                    p.sendMessage("??bRespawning in " + respawnTime + "...");
                }
                respawnTime--;
            }
        }, 0L, 20L);
    }

    private Inventory generateKitInventory(Player p) {
        List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
        int invSize = (2 + (kits.size() / 9)) * 9;
        Inventory inv = Bukkit.createInventory(p, invSize, "Deathmatch on " + arena.getName());
        for (int i = 0; i < kits.size(); i++) {
            inv.setItem(i, (ItemStack) kits.get(i).get("item"));
        }

        for (int i = invSize - 9; i < invSize; i++) {
            if (i % 9 == 4) {
                DeathmatchState ds = getState(p);
                if (ds.selectedKit == null) continue;
                inv.setItem(i, (ItemStack) kits.get(ds.selectedKit).get("item"));
            } else {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        return inv;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        DeathmatchState clickerState = getState(clicker);
        if (clickerState == null || !clickerState.selectingKit) return;

        // player must be currently selecting a kit, cancel all clicks and handle from there
        event.setCancelled(true);

        // don't handle a click unless it is on one of the kits
        if (event.getSlot() >= indexToLoadoutName.size() || event.getSlot() < 0) return;
        clickerState.selectedKit = event.getSlot();
        CVLoadouts.getInstance().applyLoadoutToPlayer(clicker, indexToLoadoutName.get(clickerState.selectedKit), List.of((String) teams.get(clickerState.team).get("loadout-team")));
        if (clickerState.hasDied) {
            event.getWhoClicked().closeInventory();
        } else {
            spawnPlayerIntoGame(clicker);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // only allow ppl to close their inv if they are done with kit sel
        if (!(event.getPlayer() instanceof Player)) return;
        DeathmatchState clickerState = (DeathmatchState) state.get((Player) event.getPlayer());
        if (clickerState == null) return;
        Bukkit.getScheduler().scheduleSyncDelayedTask(CVCombat.getInstance(), () -> {
            if (clickerState.selectingKit) {
                event.getPlayer().openInventory(generateKitInventory((Player) event.getPlayer()));
            }
        }, 10L);
    }

    public void spawnPlayerIntoGame(Player player) {
        DeathmatchState pState = (DeathmatchState) state.get(player);
        pState.selectingKit = false;
        player.closeInventory();
        int respawnIndex = teamIndexToTpIndex.get(pState.team);
        List<Location> tps = (List<Location>) teams.get(pState.team).get("tps");
        player.teleport(tps.get(respawnIndex));
        respawnIndex++;
        if (respawnIndex >= tps.size()) { respawnIndex = 0; }
        teamIndexToTpIndex.set(pState.team, respawnIndex);
    }

    @Override
    public void onGameFinish() {
        for (Player player : state.keySet()) {
            DeathmatchState ds = (DeathmatchState) state.get(player);
            if (state.containsKey(player) && ds.respawnTimer != -1) {
                Bukkit.getScheduler().cancelTask(ds.respawnTimer);
                ds.respawnTimer = -1;
            }
            player.setHealth(20);
        }

        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;

        if (error != null) {
            GameUtils.messagePlayerList(state.keySet(), "??c??lERROR: ??c" + error);
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

        state.keySet().forEach(p -> {
            p.sendMessage("??b??l--- FINAL RESULTS ---");
            sortedPlayers.forEach(player -> {
                p.sendMessage(chatColor + player.getDisplayName() + "??f: " + getState(player).kills + " kills");
            });
            playerPostGame(p);
        });
    }

    private void playerPostGame(Player p) {
        sendStatistics(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private void sendStatistics(Player player) {
        DeathmatchState ds = getState(player);
        player.sendMessage("??7Kills: ??f" + ds.kills);
        player.sendMessage("??7Deaths: ??f" + ds.deaths);
        if (ds.kills == 0) {
            player.sendMessage("??7K/D Ratio: ??f0.00");
        } else if (ds.deaths == 0) {
            player.sendMessage("??7K/D Ratio: ??f" + ds.kills + ".00");
        } else {
            player.sendMessage("??7K/D Ratio: ??f" + String.format("%.2f", ((float) ds.kills / (float) ds.deaths)));
        }
    }


    private void finishTeamGame() {
        List<Integer[]> sortedTeams = teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
        if (Objects.equals(sortedTeams.get(0)[1], sortedTeams.get(1)[1])) {
            state.keySet().forEach(p -> {
                p.sendMessage("??f??lTie Game!");
            });
        } else {
            String teamName = (String) teams.get(sortedTeams.get(0)[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(sortedTeams.get(0)[0]).get("chat-color");
            state.keySet().forEach(p -> {
                p.sendMessage(chatColor + "??l" + teamName + chatColor + "??l has won the game!");
            });
        }
        state.keySet().forEach(p -> {
            p.sendMessage("??b??l--- FINAL RESULTS ---");
            sortedTeams.forEach(pair -> {
                String teamName = (String) teams.get(pair[0]).get("name");
                ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
                if (pair[1] == -999) {
                    p.sendMessage(chatColor + teamName + "??f: ??cLeft Game");
                } else {
                    p.sendMessage(chatColor + teamName + "??f: " + pair[1] + " kills");
                }
            });
            playerPostGame(p);
        });
    }

    private void updateScoreboard() {
        Scoreboard scoreboard;
        ArrayList<String> scoreboardLines = new ArrayList<>();

        scoreboardLines.add("??bTime remaining: ??f" +
                String.format("%d:%02d", (int) currentTime / 60000, (int) (currentTime / 1000) % 60)
        );
        scoreboardLines.add("   ");

        if (teams.size() == 1) {
            state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).kills)).forEach( p -> {
                int points = getState(p).kills;
                scoreboardLines.add("??a" + p.getDisplayName() + "??f: " + points + " kills");
            });
            scoreboard = GameUtils.createScoreboard(arena, "??b??lFFA Deathmatch", scoreboardLines);
        } else {
            for (int i = 0; i < teamScores.size(); i++) {
                String line = teams.get(i).get("name") + "??f: ";
                if (teamScores.get(i)[1] == -999) {
                    line += "??cLeft Game";
                } else {
                    line += teamScores.get(i)[1];
                    line += " kills";
                }
                scoreboardLines.add(line);
            }
            scoreboard = GameUtils.createScoreboard(arena, "??b??lTeam Deathmatch", scoreboardLines);
        }
        this.state.keySet().forEach(p -> p.setScoreboard(scoreboard));
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        // check if the hit is within the game
        if (!(e.getEntity() instanceof Player)) return;
        Player damager;
        if (e.getDamager() instanceof Player) {
            damager = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile arrow = (Projectile) e.getDamager();
            if (!(arrow.getShooter() instanceof Player)) return;
            damager = (Player) arrow.getShooter();
        } else { return; }
        DeathmatchState damagerState = getState(damager);
        Player hit = (Player) e.getEntity();
        DeathmatchState hitState = getState(hit);
        if (damagerState == null || hitState == null) return;

        // prevent friendly fire, if applicable
        if (teams.size() > 1 && !((Boolean) getVariable("friendly-fire")) && damagerState.team == hitState.team)  {
            e.getDamager().sendMessage("??cDon't hit your teammates!");
            e.setCancelled(true);
            return;
        }

        // only continue past this point if this is the killing blow
        if (e.getFinalDamage() < hit.getHealth()) return;
        e.setCancelled(true);

        hitState.deaths += 1;
        hitState.hasDied = true;
        damagerState.kills += 1;
        teamScores.set(damagerState.team, new Integer[]{ damagerState.team, teamScores.get(damagerState.team)[1] + 1 });

        // Send message to everyone abt the kill
        ChatColor damagerChatColor = (ChatColor) teams.get(damagerState.team).get("chat-color");
        ChatColor hitChatColor = (ChatColor) teams.get(hitState.team).get("chat-color");
        state.keySet().forEach(player ->
                player.sendMessage(damagerChatColor + damager.getDisplayName() + "??e has killed " + hitChatColor + hit.getDisplayName())
        );

        int maxScore = (int) getVariable("max-score");
        if (teams.size() > 1) {
            if (teamScores.get(damagerState.team)[1] >= maxScore) {
                finishGame();
                return;
            }
        } else {
            if (damagerState.kills >= maxScore) {
                finishGame();
                return;
            }
        }
        forceKitSelection(hit);
        updateScoreboard();
    }
}
