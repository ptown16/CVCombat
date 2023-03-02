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
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;

import java.util.*;
import java.util.stream.Collectors;

public class Deathmatch extends TeamSelectorGame {

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
        addGameVariableObjectList("kits", new HashMap<>(){{
            put("item", new GameVariableItem("The item used to represent the kit in the GUI"));
            put("loadout", new GameVariableString("The loadout used for the kit"));
        }}, "The loadout kits that can be used in this arena");
        addGameVariableTeamsList(new HashMap<>(){{
            put("loadout-team", new GameVariableString("The team used for loadouts"));
            put("tps", new GameVariableList<>(GameVariableLocation.class, "The locations that players on this team will spawn in at"));
        }});
        addGameVariable("initial-spawn-time", new GameVariableInt("The amount of time before a player respawns into the arena when spawned in for the first time"), 15);
        addGameVariable("respawn-time", new GameVariableInt("The amount of time before a player respawns into the arena"), 10);
        addGameVariable("max-score", new GameVariableInt("The max score a team can get before they win"), 20);
        addGameVariable("friendly-fire", new GameVariableFlag("Wether players can kill others on their own team or not"), false);
        addGameVariable("duration", new GameVariableInt("The max amount of time a game lasts (in minutes)"), 10);
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
        hasSpawned = false;

        List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
        for (HashMap<String, Object> kit : kits) {
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

                Location tpLoc = (Location) getVariable("spectator-spawn");
                if (!tpLoc.getChunk().isLoaded()) {
                    tpLoc.getChunk().load();
                }

                if (teams.size() > 1) {
                    player.sendMessage(chatColor + "You are on §l" + teamName + chatColor + "!");
                } else {
                    player.sendMessage(chatColor + "It's a free for all!");
                }
                player.sendMessage(chatColor + "First to " + getVariable("max-score") + " points wins!");
                startPlayerRespawn(player, (int) getVariable("initial-spawn-time"));
                // force open the inventory, but only on the first spawn in
                player.openInventory(generateKitInventory(player));
            }
        }
        startTime = System.currentTimeMillis();

        // add the initial spawn time to the duration
        long duration = ((((int) getVariable("duration")) * 60L) + ((int) getVariable("initial-spawn-time"))) * 1000L;
        scoreboardSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                updateDefaultScoreboard((int) currentTime, teamScores, "kills");
            } else {
                finishGame();
            }
        }, 0L, 20L);
    }

    private void applyLoadoutFromState(Player p, DeathmatchState pState) {
        if (pState.selectedKit == null) {
            // choose a random kit for the player if they havent selected anything
            pState.selectedKit = random.nextInt(indexToLoadoutName.size());
        }
        CVLoadouts.getInstance().applyLoadoutToPlayer(p, indexToLoadoutName.get(pState.selectedKit), List.of((String) teams.get(pState.team).get("loadout-team")));
    }

    private void startPlayerRespawn(Player p, int respawnTime) {
        DeathmatchState pState = getState(p);
        if (pState == null) return;
        healPlayer(p);
        p.getInventory().clear();
        addSpectator(p);
        if (pState.selectedKit != null) {
            applyLoadoutFromState(p, pState);
        }
        p.getInventory().setItem(8, KIT_SELECTION_ITEM);
        pState.respawnTimer = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), new Runnable() {
            boolean firstRun = true;
            int rsTime = respawnTime;
            @Override
            public void run() {
                if (rsTime <= 0) {
                    spawnPlayerIntoGame(p);
                    Bukkit.getScheduler().cancelTask(pState.respawnTimer);
                    pState.respawnTimer = -1;
                    // make sure the player is not on fire and has full health when tpd in game
                } else if (rsTime <= 3 || firstRun) {
                    firstRun = false;
                    p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 3.0F, 1.4F);
                    String spawnVerb = hasSpawned ? "Respawning" : "Spawning";
                    p.sendMessage("§b" + spawnVerb + " in " + rsTime + "...");
                }
                rsTime--;
            }
        }, 0L, 20L);
    }

    private String getKitInventoryName() {
        return "§lDeathmatch on " + arena.getName();
    }

    private Inventory generateKitInventory(Player p) {
        if (getState(p) == null) return null;
        List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
        int invSize = (2 + (kits.size() / 9)) * 9;
        Inventory inv = Bukkit.createInventory(p, invSize, getKitInventoryName());
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

    public void spawnPlayerIntoGame(Player player) {
        DeathmatchState pState = (DeathmatchState) state.get(player);
        int respawnIndex = teamIndexToTpIndex.get(pState.team);
        List<Location> tps = (List<Location>) teams.get(pState.team).get("tps");
        player.teleport(tps.get(respawnIndex));
        removeSpectator(player);
        applyLoadoutFromState(player, pState);
        player.closeInventory();
        respawnIndex++;
        if (respawnIndex >= tps.size()) { respawnIndex = 0; }
        teamIndexToTpIndex.set(pState.team, respawnIndex);
//        CVStats.getInstance().sendMetric("selected_kit", player, Map.of(
//                "arena", arena.getName(),
//                "game", "deathmatch",
//                "kit", indexToLoadoutName.get(pState.selectedKit)
//        ));

        healPlayer(player);

        hasSpawned = true;
    }

    private void healPlayer(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);
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
        } else {
            String teamName = (String) teams.get(sortedTeams.get(0)[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(sortedTeams.get(0)[0]).get("chat-color");
            sendMessageToArena(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
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

//        CVStats.getInstance().sendMetric("pvp_death", hit, Map.of(
//            "arena", arena.getName(),
//            "game", "deathmatch",
//            "kit", indexToLoadoutName.get(hitState.selectedKit),
//            "killedBy", damager.getUniqueId().toString()
//        ));

        if (killerState == null || killer == null) {
            // if we can't find a killer, subtract 1 from the score of the team
            teamScores.set(hitState.team, new Integer[]{ hitState.team, teamScores.get(hitState.team)[1] - 1 });
            sendMessageToArena(deathMessage);
        } else {
            // if we have a killer, add one to their team's total
            killerState.kills += 1;
            teamScores.set(killerState.team, new Integer[]{ killerState.team, teamScores.get(killerState.team)[1] + 1 });

//            CVStats.getInstance().sendMetric("pvp_kill", damager, Map.of(
//                "arena", arena.getName(),
//                "game", "deathmatch",
//                "kit", indexToLoadoutName.get(damagerState.selectedKit),
//                "killed", hit.getUniqueId().toString()
//            ));

            ChatColor killerChatColor = (ChatColor) teams.get(killerState.team).get("chat-color");
            deathMessage = deathMessage.replaceAll(killer.getDisplayName(), killerChatColor + killer.getDisplayName() + "§e");

            sendMessageToArena(deathMessage);

            // we only need to check to end the game if there is a killer
            int maxScore = (int) getVariable("max-score");
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
