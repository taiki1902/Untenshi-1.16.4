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
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.motion.noFreemodeOrATO;
import static me.fiveave.untenshi.utsdriver.initDriver;

class events implements Listener {

    static void toEB(utsdriver ld) {
        if (noFreemodeOrATO(ld) && ld.getLv().getMascon() != -9 && ld.getLv().getSpeed() > 20 && ld.getLv().getAtsforced() != 2 && !ld.getLv().isAtsping()) {
            // Misuse EB
            pointCounter(ld, ChatColor.YELLOW, getlang("eb_misuse") + " ", -5, "");
        }
        ld.getLv().setMascon(-9);
        ld.getLv().setAtsforced(1);
    }

    static void switchBack(utsvehicle lv) {
        utsdriver ld = lv.getLd();
        if (lv.getSpeed() == 0) {
            MinecartGroup mg = lv.getTrain();
            mg.reverse();
            if (lv.getAtodest() != null && lv.getAtospeed() != -1) {
                lv.setAtodest(null);
                lv.setAtospeed(-1);
                generalMsg(ld.getP(), ChatColor.GOLD, getlang("ato_patterncancel"));
            }
            String dirtext = lv.getTrain().head().equals(lv.getDriverseat()) ? getlang("dir_front") : getlang("dir_back");
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("sb_success") + ChatColor.GRAY + " (" + dirtext + " / " + getlang("dir_" + mg.head().getDirection().toString().toLowerCase()) + ")");
        } else {
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("sb_inmotion"));
        }
    }

    static void doorControls(utsvehicle lv, Boolean open) {
        utsdriver ld = lv.getLd();
        if (open) {
            if (lv.getSpeed() > 0.0) {
                generalMsg(ld, ChatColor.YELLOW, getlang("door_openinmotion"));
                return;
            }
            if (lv.isFixstoppos() || lv.isReqstopping()) {
                generalMsg(ld, ChatColor.YELLOW, getlang("stoppos_reqfix"));
                return;
            }
            lv.setDoordiropen(true);
            lv.setDoorconfirm(false);
            // Provide output when open door
            if (lv.getStopoutput() != null) {
                Block b = lv.getTrain().getWorld().getBlockAt(lv.getStopoutput()[0], lv.getStopoutput()[1], lv.getStopoutput()[2]);
                b.getChunk().load();
                b.setType(Material.REDSTONE_BLOCK);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    b.setType(Material.AIR);
                    lv.setStopoutput(null);
                }, 4);
            }
            // Stop penalties (If have)
            if (noFreemodeOrATO(ld)) {
                // In station EB
                if (lv.isStaeb()) {
                    pointCounter(ld, ChatColor.YELLOW, getlang("eb_stop") + " ", -5, "");
                }
                // In station accel
                if (lv.isStaaccel()) {
                    pointCounter(ld, ChatColor.YELLOW, getlang("reaccel") + " ", -5, "");
                }
            }
            lv.setStaeb(false);
            lv.setStaaccel(false);
            // ATO Stop Time Countdown, cancelled if door is closed
            atoDepartCountdown(lv);
        } else {
            lv.setDoordiropen(false);
            lv.setReqstopping(false);
            lv.setOverrun(false);
            lv.setDoorconfirm(false);
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
        return getItem(Material.STONE_AXE, ChatColor.RED, getlang("item_upwand"));
    }

    protected static ItemStack nWand() {
        return getItem(Material.IRON_AXE, ChatColor.YELLOW, getlang("item_nwand"));
    }

    protected static ItemStack downWand() {
        return getItem(Material.DIAMOND_AXE, ChatColor.GREEN, getlang("item_downwand"));
    }

    protected static ItemStack doorButton() {
        return getItem(Material.IRON_TRAPDOOR, ChatColor.GOLD, getlang("item_doorbutton"));
    }

    protected static ItemStack sbLever() {
        return getItem(Material.LEVER, ChatColor.YELLOW, getlang("item_sblever"));
    }

    protected static ItemStack ebButton() {
        return getItem(Material.STONE_BUTTON, ChatColor.DARK_RED, getlang("item_ebbutton"));
    }

    @EventHandler
    void onPlayerClicks(PlayerInteractEvent event) {
        // Init
        Player p = event.getPlayer();
        initDriver(p);
        Action ac = event.getAction();
        ItemStack item = event.getItem();
        utsdriver ld = driver.get(p);
        utsvehicle lv = ld.getLv();
        // Main Part
        if ((ac.equals(Action.LEFT_CLICK_AIR) || ac.equals(Action.LEFT_CLICK_BLOCK) || ac.equals(Action.RIGHT_CLICK_AIR) || ac.equals(Action.RIGHT_CLICK_BLOCK)) && item != null && ld.isPlaying() && !ld.isFrozen()) {
            // Either not in ATO mode, or ATO mode with EB
            if ((lv.getAtodest() == null || lv.getAtsforced() == 1) && lv.getAtsforced() != 2) {
                if (upWand().equals(item)) {
                    if (lv.getMascon() > -8) {
                        lv.setMascon(lv.getMascon() - 1);
                    } else if (lv.getMascon() == -8) {
                        toEB(ld);
                    }
                    event.setCancelled(true);
                }
                if (nWand().equals(item)) {
                    if (!lv.isAtsping()) {
                        lv.setMascon(0);
                        lv.setAtsforced(0);
                    }
                    event.setCancelled(true);
                }
                if (downWand().equals(item)) {
                    if (!lv.isAtsping() && (lv.getDooropen() == 0 || lv.getDooropen() > 0 && lv.getMascon() < 0) && lv.getMascon() < 5) {
                        lv.setMascon(lv.getMascon() + 1);
                        lv.setAtsforced(0);
                    }
                    event.setCancelled(true);
                }
            }
            if (doorButton().equals(item)) {
                event.setCancelled(true);
                boolean rev = lv.isDoordiropen();
                doorControls(lv, !rev);
            }
            if (sbLever().equals(item)) {
                switchBack(lv);
                event.setCancelled(true);
            }
            if (ebButton().equals(item) && lv.getAtsforced() != 2) {
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
                utsdriver ld = driver.get(p);
                utsvehicle lv = ld.getLv();
                if (lv != null && ld.isPlaying() && lv.getSpeed() != 0) {
                    DecimalFormat df0 = new DecimalFormat("#");
                    double spd = lv.getSpeed();
                    String sp = df0.format(spd);
                    toEB(ld);
                    lv.setCurrent(-480);
                    lv.setSpeed(0);
                    pointCounter(ld, ChatColor.YELLOW, getlang("collidebuffer") + " ", -10, " " + sp + " km/h");
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
        initDriver(p);
        utsdriver ld = driver.get(p);
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
        initDriver(p);
        utsdriver ld = driver.get(p);
        if (ld.isPlaying()) {
            restoreinitld(ld);
        }
    }
}