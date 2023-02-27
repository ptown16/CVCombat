package org.cubeville.cvcombat.sumo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.cubeville.cvcombat.CVCombat;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.models.PlayerState;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.GameVariableInt;
import org.cubeville.cvgames.vartypes.GameVariableList;
import org.cubeville.cvgames.vartypes.GameVariableLocation;
import org.cubeville.cvgames.vartypes.GameVariableRegion;

import java.util.List;
import java.util.Set;

public class Sumo extends Game {
    int testFallRegion = -1;

    public Sumo(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("spawns", new GameVariableList<>(GameVariableLocation.class, "Where the players spawn into the arena"));
        addGameVariable("fall-region", new GameVariableRegion("The region the player falls into to be eliminated"));
        addGameVariable("knockback-level", new GameVariableInt("What level of knockback the stick has (0 for no stick)"), 0);
    }

    @Override
    protected SumoState getState(Player p) {
        if (state.get(p) == null || !(state.get(p) instanceof SumoState)) return null;
        return (SumoState) state.get(p);
    }

    @Override
    public void onGameStart(Set<Player> players) {
        int knockbackLevel = (int) getVariable("knockback-level");
        ItemStack knockbackItem = null;
        if (knockbackLevel != 0) {
            knockbackItem = new ItemStack(Material.BRICK);
            ItemMeta knockbackItemMeta = knockbackItem.getItemMeta();
            knockbackItemMeta.setDisplayName("§bSumo Knockback Apple");
            knockbackItemMeta.addEnchant(Enchantment.KNOCKBACK, knockbackLevel, true);
            knockbackItem.setItemMeta(knockbackItemMeta);
        }
        int i = 0;
        List<Location> tps = (List<Location>) getVariable("spawns");

        for (Player player : players) {
            state.put(player, new SumoState());
            player.teleport(tps.get(i));
            if (knockbackItem != null) {
                player.getInventory().addItem(knockbackItem);
            }
            i++;
        }

        GameRegion fallRegion = (GameRegion) getVariable("fall-region");
        Location exit = (Location) getVariable("exit");

        // every tick, test to see if someone is in the fall region
        testFallRegion = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVCombat.getInstance(), () -> {
            boolean hasFallenOnTick = false;
            for (Player player : state.keySet()) {
                if (fallRegion.containsPlayer(player)) {
                    hasFallenOnTick = true;
                    getState(player).isAlive = false;
                    GameUtils.messagePlayerList(state.keySet(), "§d" + player.getName() + " has fallen!");
                    player.teleport(exit);
                }
            }
            // we need to separate this out, so we can catch ties if multiple players have fallen on the same tick
            if (hasFallenOnTick) {
                testFinishGame();
            }
        }, 0L, 1L);
    }

    @Override
    public void onPlayerLeave(Player p) {
        state.remove(p);
        GameUtils.messagePlayerList(state.keySet(), "§d" + p.getName() + " has left the game!");
        testFinishGame();
    }

    @Override
    public void onGameFinish() {
        Player winner = null;
        Bukkit.getScheduler().cancelTask(testFallRegion);
        for (Player player : state.keySet()) {
            if (getState(player).isAlive) {
                winner = player;
            }
        }
        if (winner == null) {
            GameUtils.messagePlayerList(state.keySet(), "§7§lYou tied!");
        } else {
            GameUtils.messagePlayerList(state.keySet(), "§7§l" + winner.getName() + " has won!");
        }
    }

    private void testFinishGame() {
        int alivePlayerCount = 0;
        for (PlayerState state : state.values()) {
            if (((SumoState) state).isAlive) {
                if (alivePlayerCount >= 1) {
                    // if there is more than one player alive
                    // continue the game
                    return;
                }
                alivePlayerCount += 1;
            }
        }
        // if it gets here, there's only one player alive, so we finish the game
        finishGame();
    }
}
