package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Objects;

import static me.fiveave.untenshi.ato.atoDepartCountdown;
import static me.fiveave.untenshi.cmds.absentDriver;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.motion.freemodeNoATO;

class events implements Listener {

    static void toEB(untenshi ld) {
        if (freemodeNoATO(ld) && ld.getMascon() != -9 && ld.getSpeed() > 20 && !ld.isForcedbraking() && !ld.isAtsping()) {
            // Misuse EB
            pointCounter(ld, ChatColor.YELLOW, getlang("misuseeb"), -5, "");
        }
        ld.setMascon(-9);
        ld.setAtsforced(1);
    }

    static void switchBack(untenshi ld) {
        if (ld.getSpeed() == 0) {
            ld.getTrain().reverse();
            if (ld.getAtodest() != null && ld.getAtospeed() != -1) {
                ld.setAtodest(null);
                ld.setAtospeed(-1);
                generalMsg(ld.getP(), ChatColor.GOLD, getlang("atopatterncancel"));
            }
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("sbsuccess") + ChatColor.GRAY + " (" + Objects.requireNonNull(ld.getTrain().head()).getDirection() + ")");
        } else {
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("sbinmotion"));
        }
    }

    static void doorControls(untenshi ld, Boolean open) {
        if (open) {
            if (ld.getSpeed() > 0.0) {
                generalMsg(ld.getP(), ChatColor.YELLOW, getlang("dooropeninmotion"));
                return;
            }
            if (ld.isFixstoppos() || ld.isReqstopping()) {
                generalMsg(ld.getP(), ChatColor.YELLOW, getlang("fixstoppos"));
                return;
            }
            ld.setDoordiropen(true);
            ld.setDoorconfirm(false);
            // Provide output when open door
            if (ld.getStopoutput() != null) {
                Block b = ld.getP().getWorld().getBlockAt(ld.getStopoutput()[0], ld.getStopoutput()[1], ld.getStopoutput()[2]);
                b.getChunk().load();
                b.setType(Material.REDSTONE_BLOCK);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    b.setType(Material.AIR);
                    ld.setStopoutput(null);
                }, 4);
            }
            // Stop penalties (If have)
            if (freemodeNoATO(ld)) {
                // In station EB
                if (ld.isStaeb()) {
                    ld.setStaeb(false);
                    pointCounter(ld, ChatColor.YELLOW, getlang("ebstop"), -5, "");
                }
                // In station accel
                if (ld.isStaaccel()) {
                    ld.setStaaccel(false);
                    pointCounter(ld, ChatColor.YELLOW, getlang("reaccel"), -5, "");
                }
            }
            // ATO Stop Time Countdown, cancelled if door is closed
            atoDepartCountdown(ld);
        } else {
            ld.setDoordiropen(false);
            ld.setReqstopping(false);
            ld.setOverrun(false);
            ld.setDoorconfirm(false);
        }
    }

    // Simplify
    protected static ItemStack getItem(Material m, ChatColor color, String name) {
        ItemStack wand = new ItemStack(m, 1);
        ItemMeta wanditm = wand.getItemMeta();
        Objects.requireNonNull(wanditm).setDisplayName(color + name);
        wand.setItemMeta(wanditm);
        wand.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
        return wand;
    }

    static ItemStack upWand() {
        return getItem(Material.STONE_AXE, ChatColor.RED, getlang("upwandname"));
    }

    protected static ItemStack nWand() {
        return getItem(Material.IRON_AXE, ChatColor.YELLOW, getlang("nwandname"));
    }

    protected static ItemStack downWand() {
        return getItem(Material.DIAMOND_AXE, ChatColor.GREEN, getlang("downwandname"));
    }

    protected static ItemStack doorButton() {
        return getItem(Material.IRON_TRAPDOOR, ChatColor.GOLD, getlang("doorbuttonname"));
    }

    protected static ItemStack sbLever() {
        return getItem(Material.LEVER, ChatColor.YELLOW, getlang("sblevername"));
    }

    protected static ItemStack ebButton() {
        return getItem(Material.STONE_BUTTON, ChatColor.DARK_RED, getlang("ebbuttonname"));
    }

    @EventHandler
    void onPlayerClicks(PlayerInteractEvent event) {
        // Init
        Player p = event.getPlayer();
        Action ac = event.getAction();
        ItemStack item = event.getItem();
        absentDriver(p);
        untenshi ld = driver.get(p);
        // Main Part
        if ((ac.equals(Action.LEFT_CLICK_AIR) || ac.equals(Action.LEFT_CLICK_BLOCK) || ac.equals(Action.RIGHT_CLICK_AIR) || ac.equals(Action.RIGHT_CLICK_BLOCK)) && item != null && ld.isPlaying() && !ld.isFrozen()) {
            if (ld.getAtodest() == null || ld.getAtsforced() == 1) {
                if (upWand().equals(item)) {
                    if (ld.getMascon() > -8) {
                        ld.setMascon(ld.getMascon() - 1);
                    } else if (ld.getMascon() == -8) {
                        toEB(ld);
                    }
                    event.setCancelled(true);
                }
                if (nWand().equals(item)) {
                    if (!ld.isAtsping() && !ld.isForcedbraking()) {
                        ld.setMascon(0);
                    }
                    event.setCancelled(true);
                }
                if (downWand().equals(item)) {
                    if (!ld.isAtsping() && !ld.isForcedbraking() && (ld.getDooropen() == 0 || ld.getDooropen() > 0 && ld.getMascon() < 0) && ld.getMascon() < 5) {
                        ld.setMascon(ld.getMascon() + 1);
                    }
                    event.setCancelled(true);
                }
            }
            if (doorButton().equals(item)) {
                event.setCancelled(true);
                boolean rev = ld.isDoordiropen();
                doorControls(ld, !rev);
            }
            if (sbLever().equals(item)) {
                switchBack(ld);
                event.setCancelled(true);
            }
            if (ebButton().equals(item)) {
                toEB(ld);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    void collision(VehicleBlockCollisionEvent event) {
        try {
            MinecartGroup mg = MinecartGroupStore.get(event.getVehicle());
            for (String s : mg.getProperties().getOwners()) {
                Player p = Bukkit.getPlayer(s);
                cmds.absentDriver(p);
                untenshi ld = driver.get(p);
                if (ld != null && ld.isPlaying() && ld.getSpeed() != 0) {
                    DecimalFormat df0 = new DecimalFormat("#");
                    double spd = ld.getSpeed();
                    String sp = df0.format(spd);
                    toEB(ld);
                    ld.setCurrent(-480);
                    ld.setSpeed(0);
                    pointCounter(ld, ChatColor.YELLOW, getlang("collidebuffer"), -10, " " + sp + " km/h");
                }
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    void onDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isItems(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onClickItem(InventoryClickEvent event) {
        Player p = (Player) event.getWhoClicked();
        cmds.absentDriver(p);
        untenshi ld = driver.get(p);
        if (ld.isPlaying()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (isItems(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onCreativeMoveItem(InventoryCreativeEvent event) {
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
    void onLeave(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        absentDriver(p);
        untenshi ld = driver.get(p);
        if (ld.isPlaying()) {
            restoreinit(ld);
        }
    }
}