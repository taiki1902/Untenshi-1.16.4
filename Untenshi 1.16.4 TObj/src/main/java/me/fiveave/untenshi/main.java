package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.motion.noFreemodeOrATO;
import static me.fiveave.untenshi.signalsign.resetSignals;

public final class main extends JavaPlugin implements Listener {

    static final int ticksin1s = 20;
    static final int tickdelay = 20 / ticksin1s;
    static final int maxspeed = 360;
    static final double cartYPosDiff = 0.0625;
    static HashMap<MinecartGroup, utsvehicle> vehicle = new HashMap<>();
    static HashMap<Player, utsdriver> driver = new HashMap<>();
    public static main plugin;
    static abstractfile config;
    static abstractfile langdata;
    static abstractfile traindata;
    static abstractfile playerdata;
    static abstractfile signalorder;
    static String pureutstitle = ChatColor.YELLOW + "[========== " + ChatColor.GREEN + "Untenshi " + ChatColor.YELLOW + "==========]\n";
    static String utshead = "[" + ChatColor.GREEN + "Untenshi" + ChatColor.WHITE + "] ";
    stoppos sign1 = new stoppos();
    speedsign sign2 = new speedsign();
    signalsign sign3 = new signalsign();
    atosign sign4 = new atosign();
    utstrain sign5 = new utstrain();

    static String getlang(String path) {
        langdata.reloadConfig();
        String result;
        try {
            result = langdata.dataconfig.getString(path);
        } catch (Exception e) {
            result = " (Path does not exist: " + path + ") ";
        }
        return result;
    }

    static boolean noSignPerm(SignChangeActionEvent e) {
        if (!e.getPlayer().hasPermission("uts.sign")) {
            e.getPlayer().sendMessage(ChatColor.RED + getlang("noperm"));
            e.setCancelled(true);
            return true;
        }
        return false;
    }

    static void pointCounter(utsdriver ld, ChatColor color, String s, int pts, String str) {
        if (ld != null) {
            ChatColor color2 = pts > 0 ? ChatColor.GREEN : ChatColor.RED;
            String ptsstr = !noFreemodeOrATO(ld) ? "" : String.valueOf(pts);
            generalMsg(ld.getP(), color, s + color2 + ptsstr + str);
            if (noFreemodeOrATO(ld)) {
                ld.setPoints(ld.getPoints() + pts);
            }
        }
    }


    static String getSpeedMax() {
        return ChatColor.RED + getlang("speedmax");
    }

    static void restoreinitld(utsdriver ld) {
        // Get train group and stop train and open doors
        if (ld.isPlaying()) {
            if (ld.getLv().getAtodest() == null || ld.getLv().getAtospeed() == -1) {
                try {
                    MinecartGroup mg = ld.getLv().getTrain();
                    TrainProperties tprop = mg.getProperties();
                    tprop.setSpeedLimit(0);
                    mg.setForwardForce(0);
                    // Delete owners
                    tprop.clearOwners();
                } catch (Exception ignored) {
                }
                ld.getLv().setSpeed(0.0);
                ld.getLv().setDooropen(0);
                ld.getLv().setMascon(-8);
            }
            // Clear Inventory
            for (int i = 0; i < 41; i++) {
                ld.getP().getInventory().setItem(i, new ItemStack(Material.AIR));
            }
            // Reset inventory
            ld.getP().getInventory().setContents(ld.getInv());
            ld.getP().updateInventory();
            ld.getLv().setLd(null);
            try {
                driver.put(ld.getP(), new utsdriver(ld.getP(), ld.isFreemode(), ld.isAllowatousage()));
            } catch (Exception ignored) {
            }
            ld.setPlaying(false);
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("activate") + " " + ChatColor.RED + getlang("activate_off"));
        }
    }

    static void restoreinitlv(utsvehicle lv) {
        // Get train group and stop train and open doors
        try {
            MinecartGroup mg = lv.getTrain();
            TrainProperties tprop = mg.getProperties();
            tprop.setSpeedLimit(0);
            mg.setForwardForce(0);
            tprop.setPlayersEnter(true);
            tprop.setPlayersExit(true);
            // Delete owners
            tprop.clearOwners();
        } catch (Exception ignored) {
        }
        // Reset signals (resettablesign)
        final Location[] locs = lv.getResettablesisign();
        resetSignals(lv.getSavedworld(), locs);
        // Reset signals (ilposoccupied)
        final Location[] locs2 = lv.getIlposoccupied();
        resetSignals(lv.getSavedworld(), locs2);
        vehicle.put(lv.getTrain(), new utsvehicle(lv.getTrain()));
    }


    @Override
    public void onEnable() {
        // Plugin startup logic
        SignAction.register(sign1);
        SignAction.register(sign2);
        SignAction.register(sign3);
        SignAction.register(sign4);
        SignAction.register(sign5);
        plugin = this;
        // If langdata not init twice will cause UTF-8 characters not formatted properly
        config = new abstractfile(this, "config.yml");
        for (String s : Arrays.asList("en_US", "zh_TW", "JP", plugin.getConfig().getString("lang"))) {
            langdata = new abstractfile(this, "lang_" + s + ".yml");
        }
        traindata = new abstractfile(this, "traindata.yml");
        playerdata = new abstractfile(this, "playerdata.yml");
        signalorder = new abstractfile(this, "signalorder.yml");
        this.saveDefaultConfig();
        PluginManager pm = this.getServer().getPluginManager();
        Objects.requireNonNull(this.getCommand("uts")).setExecutor(new cmds());
        Objects.requireNonNull(this.getCommand("uts")).setTabCompleter(new cmds());
        Objects.requireNonNull(this.getCommand("utssignal")).setExecutor(new signalcmd());
        Objects.requireNonNull(this.getCommand("utssignal")).setTabCompleter(new signalcmd());
        Objects.requireNonNull(this.getCommand("utslogger")).setExecutor(new driverlog());
        Objects.requireNonNull(this.getCommand("utslogger")).setTabCompleter(new driverlog());
        pm.registerEvents(new events(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : driver.keySet()) {
            restoreinitld(driver.get(p));
        }
        for (MinecartGroup mg : vehicle.keySet()) {
            restoreinitlv(vehicle.get(mg));
        }
        SignAction.unregister(sign1);
        SignAction.unregister(sign2);
        SignAction.unregister(sign3);
        SignAction.unregister(sign4);
        SignAction.unregister(sign5);
    }
}
