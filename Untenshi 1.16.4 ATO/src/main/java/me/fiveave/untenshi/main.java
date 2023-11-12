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
import static me.fiveave.untenshi.motion.freemodeNoATO;
import static me.fiveave.untenshi.signalsign.resetSignals;

public final class main extends JavaPlugin implements Listener {

    static final int ticksin1s = 20;
    static final int tickdelay = 20 / ticksin1s;
    static final int maxspeed = 360;
    static final double cartYPosDiff = 0.0625;
    public static HashMap<Player, untenshi> driver = new HashMap<>();
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
        return Objects.requireNonNull(langdata.dataconfig.getString(path));
    }

    static boolean noSignPerm(SignChangeActionEvent e) {
        if (!e.getPlayer().hasPermission("uts.sign")) {
            e.getPlayer().sendMessage(ChatColor.RED + getlang("noperm"));
            e.setCancelled(true);
            return true;
        }
        return false;
    }

    static void pointCounter(untenshi ld, ChatColor color, String s, int pts, String str) {
        ChatColor color2 = pts > 0 ? ChatColor.GREEN : ChatColor.RED;
        String ptsstr = !freemodeNoATO(ld) ? "" : String.valueOf(pts);
        generalMsg(ld.getP(), color, s + color2 + ptsstr + str);
        if (freemodeNoATO(ld)) {
            ld.setPoints(ld.getPoints() + pts);
        }
    }


    static String getSpeedMax() {
        return ChatColor.RED + getlang("speedmax").replaceAll("%speed%", String.valueOf(maxspeed));
    }

    static void restoreinit(untenshi ld) {
        // Get train group and stop train and open doors
        if (ld.isPlaying()) {
            MinecartGroup mg = ld.getTrain();
            TrainProperties trainprop = mg.getProperties();
            trainprop.setSpeedLimit(0);
            mg.setForwardForce(0);
            trainprop.setPlayersEnter(true);
            trainprop.setPlayersExit(true);
            ld.setPlaying(false);
            ld.setSpeed(0.0);
            ld.setSignallimit(maxspeed);
            ld.setSpeedlimit(maxspeed);
            ld.setFrozen(false);
            ld.setDooropen(0);
            ld.setDoordiropen(false);
            ld.setDoorconfirm(false);
            ld.setFixstoppos(false);
            ld.setStaeb(false);
            ld.setStaaccel(false);
            ld.setMascon(-9);
            ld.setCurrent(-480.0);
            ld.setPoints(30);
            ld.setForcedbraking(false);
            ld.setAtsping(false);
            ld.setAtspnear(false);
            ld.setOverrun(false);
            ld.setSafetysystype("ats-p");
            ld.setReqstopping(false);
            ld.setAtsforced(0);
            ld.setAtopisdirect(false);
            ld.setAtoforcebrake(false);
            ld.setTrain(null);
            ld.setStoppos(null);
            ld.setAtospeed(-1);
            ld.setAtodest(null);
            ld.setAtostoptime(-1);
            ld.setLastsisign(null);
            ld.setLastspsign(null);
            ld.setLastsisp(maxspeed);
            ld.setLastspsp(maxspeed);
            // Delete owners
            trainprop.clearOwners();
            // Clear Inventory
            for (int i = 0; i < 41; i++) {
                ld.getP().getInventory().setItem(i, new ItemStack(Material.AIR));
            }
            // Reset inventory
            ld.getP().getInventory().setContents(ld.getInv());
            ld.getP().updateInventory();
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("activate") + ChatColor.RED + getlang("disable"));
            // Reset signals (resettablesign)
            final Location[] locs = ld.getResettablesisign();
            resetSignals(ld.getP().getWorld(), locs);
            // Reset signals (ilposoccupied)
            final Location[] locs2 = ld.getIlposoccupied();
            resetSignals(ld.getP().getWorld(), locs2);
            ld.setResettablesisign(null);
            ld.setSignalorderptn("default");
            ld.setIlposlist(null);
            ld.setIlposoccupied(null);
            ld.setIlenterqueuetime(0);
        }
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
            // Prevent plugin shutdown affecting playing status
            restoreinit(driver.get(p));
        }
        SignAction.unregister(sign1);
        SignAction.unregister(sign2);
        SignAction.unregister(sign3);
        SignAction.unregister(sign4);
        SignAction.unregister(sign5);
    }
}
