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
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;

import java.util.*;
import java.util.stream.Collectors;

public abstract class PvPFFAGame extends Game {

    protected boolean hasSpawned = false;
    protected ArrayList<String> indexToLoadoutName = new ArrayList<>();
    private final Random random = new Random();
    private int tpSpawnIndex = 0;

    public PvPFFAGame(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("respawn-time", new GameVariableInt("The amount of time before a player respawns into the arena"), 10);
        addGameVariable("ffa-tps", new GameVariableList<>(GameVariableLocation.class, "The locations that players will spawn in at"));
        addGameVariable("ffa-chat-color", new GameVariableChatColor("The chat color used in a FFA match."));
        if (usesDefaultKits()) {
            addGameVariable("ffa-loadout-team", new GameVariableString("The team used for loadouts"));
            addGameVariable("initial-spawn-time", new GameVariableInt("The amount of time before a player respawns into the arena when spawned in for the first time"), 15);addGameVariable("initial-spawn-time", new GameVariableInt("The amount of time before a player respawns into the arena when spawned in for the first time"), 15);
            addGameVariableObjectList("kits", new HashMap<>(){{
                put("item", new GameVariableItem("The item used to represent the kit in the GUI"));
                put("loadout", new GameVariableString("The loadout used for the kit"));
            }}, "The loadout kits that can be used in this arena");
        }
    }
    public abstract PvPGameOptions getOptions();

    public abstract List<Player> getSortedPlayers();

    protected boolean usesDefaultKits() {
        return getOptions().getKitsEnabled() && getOptions().getDefaultKits();
    }

    public void onGameStart(Set<Player> players) {
        hasSpawned = false;
        if (usesDefaultKits()) {
            List<HashMap<String, Object>> kits = (List<HashMap<String, Object>>) getVariable("kits");
            for (HashMap<String, Object> kit : kits) {
                indexToLoadoutName.add((String) kit.get("loadout"));
            }
        }

        ChatColor chatColor = (ChatColor) getVariable("ffa-chat-color");

        for (Player player : players) {
            Location tpLoc = (Location) getVariable("spectator-spawn");
            if (!tpLoc.getChunk().isLoaded()) {
                tpLoc.getChunk().load();
            }

            player.sendMessage(chatColor + "It's a free for all!");
            int initialSpawnTime = usesDefaultKits() ? (int) getVariable("initial-spawn-time") : 0;
            startPlayerRespawn(player, initialSpawnTime);
            // force open the inventory, but only on the first spawn in
            if (usesDefaultKits()) {
                player.openInventory(generateKitInventory(player));
            }
        }
    }

    @Override
    protected abstract PvPPlayerState getState(Player player);

    public void onPlayerLeave(Player p) {
        PvPPlayerState ps = getState(p);
        if (ps == null) return;
        Bukkit.getScheduler().cancelTask(ps.respawnTimer);
        healPlayer(p);
        GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
                "arena", arena.getName(),
                "game", getId(),
                "player", p.getUniqueId().toString(),
                "result", "leave"
        ));
        ps.respawnTimer = -1;
        state.remove(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    public void onGameFinish() {
        for (Player player : state.keySet()) {
            PvPPlayerState ps = (PvPPlayerState) state.get(player);
            if (state.containsKey(player) && ps.respawnTimer != -1) {
                Bukkit.getScheduler().cancelTask(ps.respawnTimer);
                ps.respawnTimer = -1;
            }
            healPlayer(player);
        }

        List<Player> sortedPlayers  = getSortedPlayers();
        ChatColor chatColor = (ChatColor) getVariable("ffa-chat-color");
        int highScore = sortedPlayers.size() > 0 ? getState(sortedPlayers.get(0)).getSortingValue() : -1;

        if (sortedPlayers.size() > 1 && highScore == getState(sortedPlayers.get(1)).getSortingValue()) {
            state.keySet().forEach(p -> {
                StringBuilder output = new StringBuilder();
                output.append(chatColor);
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    if (i == (sortedPlayers.size() - 1) || highScore != getState(sortedPlayers.get(i + 1)).getSortingValue()) {
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
        sendPlayerResultMetrics(highScore);
        indexToLoadoutName.clear();
    }

    protected void startPlayerRespawn(Player p, int respawnTime) {
        PvPPlayerState pState = getState(p);
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
        PvPPlayerState pState = getState(player);
        List<Location> tps = (List<Location>) getVariable("ffa-tps");
        player.teleport(tps.get(tpSpawnIndex));
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
        tpSpawnIndex++;
        if (tpSpawnIndex >= tps.size()) { tpSpawnIndex = 0; }
        healPlayer(player);

        hasSpawned = true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getKitInventoryName())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        PvPPlayerState clickerState = getState(clicker);
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

    protected void applyLoadoutFromState(Player p, PvPPlayerState pState) {
        if (pState.selectedKit == null) {
            // choose a random kit for the player if they havent selected anything
            pState.selectedKit = random.nextInt(indexToLoadoutName.size());
        }
        CVLoadouts.getInstance().applyLoadoutToPlayer(p, indexToLoadoutName.get(pState.selectedKit), List.of((String) getVariable("ffa-loadout-team")));
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
                PvPPlayerState pState = getState(p);
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

    public void onPlayerDeath(PlayerDeathEvent e) {
        onPlayerDeath(e, true);
    }

    public void onPlayerDeath(PlayerDeathEvent e, boolean doRespawn) {
        Player killer = e.getEntity().getKiller();
        PvPPlayerState killerState = getState(killer);
        Player hit = e.getEntity();
        PvPPlayerState hitState = getState(hit);

        // make sure the player drops nothing on death
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        hit.getInventory().clear();

        hitState.deaths += 1;
        ChatColor chatColor = (ChatColor) getVariable("ffa-chat-color");
        // grab a copy of the death message and set the server-wide death message to null
        String deathMessage = "§e" + e.getDeathMessage();
        e.setDeathMessage(null);
        deathMessage = deathMessage.replaceAll(hit.getDisplayName(), chatColor + hit.getDisplayName() + "§e");

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

            deathMessage = deathMessage.replaceAll(killer.getDisplayName(), chatColor + killer.getDisplayName() + "§e");

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

    protected void sendPlayerResultMetrics(int maxValue) {
        state.keySet().forEach(player -> {
            PvPPlayerState pState = getState(player);
            String result = pState.getSortingValue() == maxValue ? "win" : "loss";
            GameUtils.sendMetricToCVStats("pvp_player_result", Map.of(
                    "arena", arena.getName(),
                    "game", getId(),
                    "player", player.getUniqueId().toString(),
                    "result", result
            ));
        });
    }
}
