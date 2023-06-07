package org.cubeville.cvcombat.models;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvcombat.deathmatch.DeathmatchState;
import org.cubeville.cvgames.models.BaseGame;
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;

import java.util.*;

public abstract class PvPTeamSelectorGame extends TeamSelectorGame {

    protected String error;
    protected int scoreboardSecondUpdater;
    protected List<HashMap<String, Object>> teams;
    protected long startTime = 0;
    protected long currentTime;
    protected boolean hasSpawned = false;
    protected ArrayList<String> indexToLoadoutName = new ArrayList<>();
    protected ArrayList<Integer> teamIndexToTpIndex = new ArrayList<>();

    private final ItemStack KIT_SELECTION_ITEM = GameUtils.customHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmRkMTZhOTU5ZGQ5MmE3MzU4MWJjNjE2NGQzZjgxZDQ2MjQ0YmUyOTdkMjVmYmUwNmNiNTA5NTQ4NTY1NWZkNCJ9fX0=", "§d§lSelect Kit §7§o(Right Click)");
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
        addGameVariableTeamsList(teamVariables);
    }
    public abstract PvPGameOptions getOptions();

    protected boolean usesDefaultKits() {
        if (getOptions().getKitsEnabled() && getOptions().getDefaultKits()) {
            return true;
        }
        else {
            return false;
        }
    }

    public void onPvPGameStart(List<Set<Player>> playerTeamMap) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
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
                startPlayerRespawn(player, (int) getVariable("initial-spawn-time"));
                // force open the inventory, but only on the first spawn in
                player.openInventory(generateKitInventory(player));
            }
        }
    }

    @Override
    protected abstract PvPPlayerState getState(Player player);

    protected void startPlayerRespawn(Player p, int respawnTime) {
        PvPPlayerState pState = getState(p);
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

    public void spawnPlayerIntoGame(Player player) {
        PvPPlayerState pState = getState(player);
        int respawnIndex = teamIndexToTpIndex.get(pState.team);
        List<Location> tps = (List<Location>) teams.get(pState.team).get("tps");
        player.teleport(tps.get(respawnIndex));
        removeSpectator(player);
        applyLoadoutFromState(player, pState);
        player.closeInventory();
        respawnIndex++;
        if (respawnIndex >= tps.size()) { respawnIndex = 0; }
        teamIndexToTpIndex.set(pState.team, respawnIndex);
        GameUtils.sendMetricToCVStats("spawned_kit", Map.of(
                "arena", arena.getName(),
                "game", "deathmatch",
                "kit", indexToLoadoutName.get(pState.selectedKit),
                "player", player.getUniqueId().toString()
        ));

        healPlayer(player);

        hasSpawned = true;
    }

    protected void applyLoadoutFromState(Player p, PvPPlayerState pState) {
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
        Inventory inv = Bukkit.createInventory(p, invSize, getKitInventoryName());
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

}
