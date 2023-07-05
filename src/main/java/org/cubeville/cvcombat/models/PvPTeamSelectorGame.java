package org.cubeville.cvcombat.models;

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
import org.cubeville.cvgames.enums.ArenaStatus;
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;

import java.util.*;

public abstract class PvPTeamSelectorGame extends TeamSelectorGame {

    protected String error;
    protected List<HashMap<String, Object>> teams;
    protected List<Set<Player>> teamPlayers = new ArrayList<Set<Player>>();
    protected boolean hasSpawned = false;
    protected ArrayList<String> indexToLoadoutName = new ArrayList<>();
    protected ArrayList<Integer> teamIndexToTpIndex = new ArrayList<>();
    private final Random random = new Random();

    public PvPTeamSelectorGame(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("friendly-fire", new GameVariableFlag("Whether players can kill others on their own team or not"), false);
        addGameVariable("respawn-time", new GameVariableInt("The amount of time before a player respawns into the arena"), 10);
        HashMap<String, GameVariable> teamVariables = new HashMap<>();
        teamVariables.put("tps", new GameVariableList<>(GameVariableLocation.class, "The locations that players on this team will spawn in at"));
        if (usesDefaultKits()) {
            teamVariables.put("loadout-team", new GameVariableString("The team used for loadouts"));
            addGameVariable("initial-spawn-time", new GameVariableInt("The amount of time before a player respawns into the arena when spawned in for the first time"), 15);addGameVariable("initial-spawn-time", new GameVariableInt("The amount of time before a player respawns into the arena when spawned in for the first time"), 15);
            addGameVariableObjectList("kits", new HashMap<>(){{
                put("item", new GameVariableItem("The item used to represent the kit in the GUI"));
                put("loadout", new GameVariableString("The loadout used for the kit"));
            }}, "The loadout kits that can be used in this arena");
        }
        addTeamVariables(teamVariables);
        addGameVariableTeamsList(teamVariables);
    }
    public abstract PvPGameOptions getOptions();

    public abstract List<Integer[]> getSortedTeams();

    public void addTeamVariables(HashMap<String, GameVariable> variableMap) {
    }

    protected boolean usesDefaultKits() {
        if (getOptions().getKitsEnabled() && getOptions().getDefaultKits()) {
            return true;
        }
        else {
            return false;
        }
    }

    public void onPvPGameStart(List<Set<Player>> playerTeamMap) {
        hasSpawned = false;
        if (usesDefaultKits()) {
            List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
            for (HashMap<String, Object> kit : kits) {
                indexToLoadoutName.add((String) kit.get("loadout"));
            }
        }

        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = playerTeamMap.get(i);
            this.teamPlayers.add(teamPlayers);

            if (teamPlayers == null) { continue; }

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");

            teamIndexToTpIndex.add(0);

            for (Player player : teamPlayers) {
                Location tpLoc = (Location) getVariable("spectator-spawn");
                if (!tpLoc.getChunk().isLoaded()) {
                    tpLoc.getChunk().load();
                }

                if (teams.size() > 1) {
                    player.sendMessage(chatColor + "You are on §l" + teamName + chatColor + "!");
                } else {
                    player.sendMessage(chatColor + "It's a free for all!");
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
    }

    @Override
    protected abstract PvPTeamPlayerState getState(Player player);

    public void onPvPPlayerLeave(Player p) {
        PvPTeamPlayerState ps = getState(p);
        if (ps == null) return;
        Bukkit.getScheduler().cancelTask(ps.respawnTimer);
        healPlayer(p);
        GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
                "arena", arena.getName(),
                "game", getId(),
                "team", (String) teams.get(ps.team).get("name"),
                "player", p.getUniqueId().toString(),
                "result", "leave"
        ));
        ps.respawnTimer = -1;
        teamPlayers.get(getState(p).team).remove(p);
        state.remove(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    public void onPvPGameFinish() {
        for (Player player : state.keySet()) {
            PvPTeamPlayerState ps = (PvPTeamPlayerState) state.get(player);
            if (state.containsKey(player) && ps.respawnTimer != -1) {
                Bukkit.getScheduler().cancelTask(ps.respawnTimer);
                ps.respawnTimer = -1;
            }
            healPlayer(player);
        }

        if (error != null) {
            GameUtils.messagePlayerList(state.keySet(), "§c§lERROR: §c" + error);
        } else {
            List<Integer[]> sortedTeams = getSortedTeams();
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
        }
        error = null;
        indexToLoadoutName.clear();
        teamIndexToTpIndex.clear();
        teamPlayers.clear();
    }

    @Override
    public int getPlayerTeamIndex(Player player) {
        return Objects.requireNonNull(getState(player)).team;
    }

    protected boolean isLastOnTeam(Player p) {
        PvPTeamPlayerState ps = getState(p);
        if (ps == null) return false;
        for (Player player : state.keySet()) {
            if (!player.equals(p) && ((PvPTeamPlayerState) state.get(player)).team == ps.team) {
                return false;
            }
        }
        return true;
    }
    protected void startPlayerRespawn(Player p, int respawnTime) {
        PvPTeamPlayerState pState = getState(p);
        if (pState == null) return;
        healPlayer(p);
        p.getInventory().clear();
        addSpectator(p);
        if (usesDefaultKits()) {
            if (pState.selectedKit != null) {
                applyLoadoutFromState(p, pState);
            }
            p.getInventory().setItem(8, PVPUtils.KIT_SELECTION_ITEM);
        }
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

    public void spawnPlayerIntoGame(Player player) {
        PvPTeamPlayerState pState = getState(player);
        int respawnIndex = teamIndexToTpIndex.get(pState.team);
        List<Location> tps = (List<Location>) teams.get(pState.team).get("tps");
        player.teleport(tps.get(respawnIndex));
        removeSpectator(player);
        if (usesDefaultKits()) {
            applyLoadoutFromState(player, pState);
            GameUtils.sendMetricToCVStats("spawned_kit", Map.of(
                    "arena", arena.getName(),
                    "game", getId(),
                    "kit", indexToLoadoutName.get(pState.selectedKit),
                    "player", player.getUniqueId().toString()
            ));
        }
        player.closeInventory();
        respawnIndex++;
        if (respawnIndex >= tps.size()) { respawnIndex = 0; }
        teamIndexToTpIndex.set(pState.team, respawnIndex);
        healPlayer(player);

        hasSpawned = true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getKitInventoryName())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        PvPTeamPlayerState clickerState = getState(clicker);
        if (clickerState == null) return;

        // player must be currently selecting a kit, cancel all clicks and handle from there
        event.setCancelled(true);

        // don't handle a click unless it is on one of the kits
        if (event.getSlot() >= indexToLoadoutName.size() || event.getSlot() < 0) return;
        clickerState.selectedKit = event.getSlot();

        // apply the loadout to the player, so they can see what the loadout contains
        applyLoadoutFromState(clicker, clickerState);
        clicker.getInventory().setItem(8, PVPUtils.KIT_SELECTION_ITEM);
        event.getWhoClicked().closeInventory();
    }

    protected void applyLoadoutFromState(Player p, PvPTeamPlayerState pState) {
        if (pState.selectedKit == null) {
            // choose a random kit for the player if they havent selected anything
            pState.selectedKit = random.nextInt(indexToLoadoutName.size());
        }
        CVLoadouts.getInstance().applyLoadoutToPlayer(p, indexToLoadoutName.get(pState.selectedKit), List.of((String) teams.get(pState.team).get("loadout-team")));
    }

    protected void healPlayer(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);
    }

    protected Inventory generateKitInventory(Player p) {
        if (getState(p) == null) return null;
        List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
        int invSize = (2 + (kits.size() / 9)) * 9;
        Bukkit.getLogger().info("" + p + " " + invSize + " " + getKitInventoryName());
        Inventory inv = Bukkit.createInventory(p, invSize, getKitInventoryName());
        Bukkit.getLogger().info("" + inv);
        for (int i = 0; i < kits.size(); i++) {
            inv.setItem(i, (ItemStack) kits.get(i).get("item"));
        }

        for (int i = invSize - 9; i < invSize; i++) {
            if (i % 9 == 4) {
                PvPTeamPlayerState pState = getState(p);
                if (pState.selectedKit == null) continue;
                inv.setItem(i, (ItemStack) kits.get(pState.selectedKit).get("item"));
            } else {
                inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        return inv;
    }

    protected String getKitInventoryName() {
        return "Kits";
    }

    public void onPvPPlayerDamage(EntityDamageByEntityEvent e) {
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
        PvPTeamPlayerState damagerState = getState(damager);
        Player hit = (Player) e.getEntity();
        PvPTeamPlayerState hitState = getState(hit);
        if (damagerState == null || hitState == null) return;

        // prevent friendly fire, if applicable
        if (!((Boolean) getVariable("friendly-fire")) && damagerState.team == hitState.team) {
            e.getDamager().sendMessage("§cDon't hit your teammates!");
            e.setCancelled(true);
        }
    }

    public void onPvPPlayerDeath(PlayerDeathEvent e) {
        onPvPPlayerDeath(e, true);
    }

    public void onPvPPlayerDeath(PlayerDeathEvent e, boolean doRespawn) {
        Player killer = e.getEntity().getKiller();
        PvPTeamPlayerState killerState = getState(killer);
        Player hit = e.getEntity();
        PvPTeamPlayerState hitState = getState(hit);

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
                "game", getId(),
                "kit", usesDefaultKits() ? indexToLoadoutName.get(hitState.selectedKit) : "none",
                "player", hit.getUniqueId().toString()
        ));

        if (killerState == null || killer == null) {
            // if we can't find a killer, just send the message
            sendMessageToArena(deathMessage);
        } else {
            // if we have a killer, there's more to do
            killerState.kills += 1;
            GameUtils.sendMetricToCVStats("pvp_kill", Map.of(
                    "arena", arena.getName(),
                    "game", getId(),
                    "kit", usesDefaultKits() ? indexToLoadoutName.get(hitState.selectedKit) : "none",
                    "player", killer.getUniqueId().toString()
            ));

            ChatColor killerChatColor = (ChatColor) teams.get(killerState.team).get("chat-color");
            deathMessage = deathMessage.replaceAll(killer.getDisplayName(), killerChatColor + killer.getDisplayName() + "§e");

            sendMessageToArena(deathMessage);
        }

        if (doRespawn) {
            startPlayerRespawn(hit, (int) getVariable("respawn-time"));
        }

    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        // is the player holding the kit selection item?
        if (e.getItem() == null) return;
        if (!e.getItem().isSimilar(PVPUtils.KIT_SELECTION_ITEM)) return;

        // is the player in the game?
        if (getState(e.getPlayer()) == null) return;

        e.setCancelled(true);
        e.getPlayer().openInventory(generateKitInventory(e.getPlayer()));
    }

    protected void teleportOut(Player player) {
        if (getArena().getStatus() == ArenaStatus.HOSTING) {
            player.teleport((Location) getVariable("lobby"));
        } else {
            player.teleport((Location) getVariable("exit"));
        }
    }

    protected void sendTeamResultMetrics(int winningTeamIndex) {
        for (int i = 0; i < teams.size(); i++) {
            String result = i == winningTeamIndex ? "win" : "loss";
            GameUtils.sendMetricToCVStats("pvp_team_result", Map.of(
                    "arena", arena.getName(),
                    "game", getId(),
                    "team", (String) teams.get(i).get("name"),
                    "result", winningTeamIndex == -1 ? "tie" : result
            ));
        }
        state.keySet().forEach(player -> {
            PvPTeamPlayerState pState = getState(player);
            String result = pState.team == winningTeamIndex ? "win" : "loss";
            GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
                    "arena", arena.getName(),
                    "game", getId(),
                    "team", (String) teams.get(pState.team).get("name"),
                    "player", player.getUniqueId().toString(),
                    "result", winningTeamIndex == -1 ? "tie" : result
            ));
        });
    }

}
