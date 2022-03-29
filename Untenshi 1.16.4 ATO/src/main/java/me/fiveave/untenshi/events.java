package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Objects;

import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.main.*;

public class events implements Listener {

    @EventHandler
    public void onPlayerClicks(PlayerInteractEvent event) {
        // Init
        Player p = event.getPlayer();
        Action ac = event.getAction();
        ItemStack item = event.getItem();
        Integer masconstat = mascon.get(p);
        dooropen.putIfAbsent(p, 0);
        doorconfirm.putIfAbsent(p, true);
        playing.putIfAbsent(p, false);
        mascon.putIfAbsent(p, -9);
        // Main Part
        if ((ac.equals(Action.LEFT_CLICK_AIR) || ac.equals(Action.LEFT_CLICK_BLOCK) || ac.equals(Action.RIGHT_CLICK_AIR) || ac.equals(Action.RIGHT_CLICK_BLOCK)) && item != null && playing.get(p) && !frozen.get(p)) {
            if (upWand().equals(item)) {
                if (masconstat > -9) {
                    mascon.put(p, masconstat - 1);
                }
                event.setCancelled(true);
            }
            if (nWand().equals(item)) {
                if (!atsping.get(p) && !atsebing.get(p)) {
                    mascon.put(p, 0);
                }
                event.setCancelled(true);
            }
            if (downWand().equals(item)) {
                if (!atsping.get(p) && !atsebing.get(p) && (dooropen.get(p) == 0 || (dooropen.get(p) > 0 && masconstat < 0))) {
                    if (masconstat < 5) {
                        mascon.put(p, masconstat + 1);
                    }
                } else {
                    mascon.put(p, -9);
                }
                event.setCancelled(true);
            }
            if (doorButton().equals(item)) {
                if (doordiropen.containsKey(p)) {
                    Bukkit.dispatchCommand(p, "uts dooropen " + !doordiropen.get(p));
                }
                event.setCancelled(true);
            }
            if (sbLever().equals(item)) {
                Bukkit.dispatchCommand(p, "uts sb");
                event.setCancelled(true);
            }
            if (ebButton().equals(item)) {
                Bukkit.dispatchCommand(p, "uts eb");
                atsforced.put(p, 1);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void collision(VehicleBlockCollisionEvent event) {
        try {
            MinecartGroup mg = MinecartGroupStore.get(event.getVehicle());
            for (String s : mg.getProperties().getOwners()) {
                Player p = Bukkit.getPlayer(s);
                if (playing.get(p) && p != null && !speed.get(p).equals(0.0)) {
                    DecimalFormat df0 = new DecimalFormat("#");
                    double spd = speed.get(p);
                    String sp = df0.format(spd);
                    mascon.put(p, -9);
                    current.put(p, -480.0);
                    speed.put(p, 0.0);
                    if (!freemode.get(p)) {
                        points.put(p, points.get(p) - 10);
                        p.sendMessage(utshead + ChatColor.YELLOW + getlang("collidebuffer") + ChatColor.RED + "-10 " + sp + " km/h");
                    } else {
                        p.sendMessage(utshead + ChatColor.YELLOW + getlang("collidebuffer") + ChatColor.RED + sp + " km/h");
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isItems(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClickItem(InventoryClickEvent event) {
        playing.putIfAbsent((Player) event.getWhoClicked(), false);
        if (playing.get((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (isItems(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreativeMoveItem(InventoryCreativeEvent event) {
        ItemStack item = event.getCursor();
        if (isItems(item)) {
            event.setCancelled(true);
        }
    }

    private boolean isItems(ItemStack item) {
        return item.equals(upWand()) || item.equals(nWand()) || item.equals(downWand()) || item.equals(doorButton()) || item.equals(sbLever()) || item.equals(ebButton());
    }


    @EventHandler
    // Prevent player leaving affecting playing status
    public void onLeave(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        playing.putIfAbsent(p, false);
        if (playing.get(p)) {
            restoreinit(p);
        }
    }

    // Simplify
    protected static ItemStack makeItem(Material m, ChatColor color, String name) {
        ItemStack wand = new ItemStack(m, 1);
        ItemMeta wanditm = wand.getItemMeta();
        Objects.requireNonNull(wanditm).setDisplayName(color + name);
        wand.setItemMeta(wanditm);
        wand.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
        return wand;
    }

    protected ItemStack upWand() {
        return makeItem(Material.STONE_AXE, ChatColor.RED, getlang("upwandname"));
    }

    protected ItemStack nWand() {
        return makeItem(Material.IRON_AXE, ChatColor.YELLOW, getlang("nwandname"));
    }

    protected ItemStack downWand() {
        return makeItem(Material.DIAMOND_AXE, ChatColor.GREEN, getlang("downwandname"));
    }

    protected ItemStack doorButton() {
        return makeItem(Material.IRON_TRAPDOOR, ChatColor.GOLD, getlang("doorbuttonname"));
    }

    protected ItemStack sbLever() {
        return makeItem(Material.LEVER, ChatColor.YELLOW, getlang("sblevername"));
    }

    protected ItemStack ebButton() {
        return makeItem(Material.STONE_BUTTON, ChatColor.DARK_RED, getlang("ebbuttonname"));
    }
}