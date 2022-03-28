package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public final class main extends JavaPlugin implements Listener {
    static HashMap<Player, Integer> mascon = new HashMap<>();
    static HashMap<Player, Integer> speedlimit = new HashMap<>();
    static HashMap<Player, Integer> signallimit = new HashMap<>();
    static HashMap<Player, Integer> points = new HashMap<>();
    static HashMap<Player, Integer> deductdelay = new HashMap<>();
    static HashMap<Player, Integer> atsdelay = new HashMap<>();
    static HashMap<Player, Integer> atsforced = new HashMap<>();
    static HashMap<Player, Integer> lastsisp = new HashMap<>();
    static HashMap<Player, Integer> lastspsp = new HashMap<>();
    static HashMap<Player, Integer> dooropen = new HashMap<>();
    static HashMap<Player, Integer[]> stopoutput = new HashMap<>();
    static HashMap<Player, Integer[]> atodest = new HashMap<>();
    static HashMap<Player, Integer> atostoptime = new HashMap<>();
    static HashMap<Player, Double> current = new HashMap<>();
    static HashMap<Player, Double> speed = new HashMap<>();
    static HashMap<Player, Double> atospeed = new HashMap<>();
    static HashMap<Player, Double[]> stoppos = new HashMap<>();
    static HashMap<Player, Double> lasty = new HashMap<>();
    static HashMap<Player, Location> lastsisign = new HashMap<>();
    static HashMap<Player, Location> lastspsign = new HashMap<>();
    static HashMap<Player, Location[]> lastresetablesign = new HashMap<>();
    static HashMap<Player, String[]> lastresetabletxt = new HashMap<>();
    static HashMap<Player, String> traintype = new HashMap<>();
    static HashMap<Player, String> signaltype = new HashMap<>();
    static HashMap<Player, Boolean> playing = new HashMap<>();
    static HashMap<Player, Boolean> freemode = new HashMap<>();
    static HashMap<Player, Boolean> instation = new HashMap<>();
    static HashMap<Player, Boolean> overrun = new HashMap<>();
    static HashMap<Player, Boolean> fixstoppos = new HashMap<>();
    static HashMap<Player, Boolean> staaccel = new HashMap<>();
    static HashMap<Player, Boolean> staeb = new HashMap<>();
    static HashMap<Player, Boolean> atsing = new HashMap<>();
    static HashMap<Player, Boolean> atsebing = new HashMap<>();
    static HashMap<Player, Boolean> atsping = new HashMap<>();
    static HashMap<Player, Boolean> atspnear = new HashMap<>();
    static HashMap<Player, Boolean> doordiropen = new HashMap<>();
    static HashMap<Player, Boolean> doorconfirm = new HashMap<>();
    static HashMap<Player, Boolean> frozen = new HashMap<>();
    static HashMap<Player, Boolean> allowatousage = new HashMap<>();
    static HashMap<Player, Boolean> atopforcedirect = new HashMap<>();
    static HashMap<Player, MinecartGroup> train = new HashMap<>();
    static HashMap<Player, ItemStack[]> inv = new HashMap<>();
    static main plugin;
    public static abstractfile config;
    public static abstractfile langdata;
    public static abstractfile traindata;
    public static abstractfile playerdata;

    private static FileConfiguration getLConfig() {
        return langdata.dataconfig;
    }

    static String getlang(String path) {
        langdata.reloadConfig();
        return Objects.requireNonNull(getLConfig().getString(path));
    }

    stoppos v1 = new stoppos();
    speedsign v2 = new speedsign();
    signalsign v3 = new signalsign();
    atosign v4 = new atosign();
    utstrain v5 = new utstrain();

    @Override
    public void onEnable() {
        // Plugin startup logic
        SignAction.register(v1);
        SignAction.register(v2);
        SignAction.register(v3);
        SignAction.register(v4);
        SignAction.register(v5);
        plugin = this;
        // If langdata not init twice will cause UTF-8 characters not formatted properly
        config = new abstractfile(this, "config.yml");
        for (String s : Arrays.asList("en_US", "zh_TW", "JP", plugin.getConfig().getString("lang"))) {
            langdata = new abstractfile(this, "lang_" + s + ".yml");
        }
        traindata = new abstractfile(this, "traindata.yml");
        playerdata = new abstractfile(this, "playerdata.yml");
        this.saveDefaultConfig();
        PluginManager pm = this.getServer().getPluginManager();
        Objects.requireNonNull(this.getCommand("uts")).setExecutor(new cmds());
        Objects.requireNonNull(this.getCommand("uts")).setTabCompleter(new cmds());
        Objects.requireNonNull(this.getCommand("utssignal")).setExecutor(new signalcmd());
        Objects.requireNonNull(this.getCommand("utssignal")).setTabCompleter(new signalcmd());
        pm.registerEvents(new events(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : this.getServer().getOnlinePlayers()) {
            // Prevent plugin shutdown affecting playing status
            restoreinit(p);
        }
        SignAction.unregister(v1);
        SignAction.unregister(v2);
        SignAction.unregister(v3);
        SignAction.unregister(v4);
        SignAction.unregister(v5);
    }

    public static String pureutstitle = ChatColor.YELLOW + "[========== " + ChatColor.GREEN + "Untenshi " + ChatColor.YELLOW + "==========]\n";

    public static String utshead = "[" + ChatColor.GREEN + "Untenshi" + ChatColor.WHITE + "] ";

    public static void restoreinit(Player p) {
        // Get train group and stop train and open doors
        playing.putIfAbsent(p, false);
        if (playing.get(p)) {
            MinecartGroup nmg = train.get(p);
            TrainProperties ntrainprop = nmg.getProperties();
            ntrainprop.setSpeedLimit(0);
            nmg.setForwardForce(0);
            ntrainprop.setPlayersEnter(true);
            ntrainprop.setPlayersExit(true);
            speed.put(p, 0.0);
            points.put(p, 30);
            atsdelay.put(p, 0);
            overrun.put(p, false);
            instation.put(p, false);
            fixstoppos.put(p, false);
            playing.put(p, false);
            train.remove(p);
            lasty.remove(p);
            stoppos.remove(p);
            speedlimit.remove(p);
            signallimit.remove(p);
            atospeed.remove(p);
            atodest.remove(p);
            atostoptime.remove(p);
            lastsisign.remove(p);
            lastspsign.remove(p);
            lastsisp.remove(p);
            lastspsp.remove(p);
            // Delete owners
            ntrainprop.clearOwners();
            // Clear Inventory
            for (int i = 0; i < 41; i++) {
                p.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
            // Reset inventory
            p.getInventory().setContents(inv.get(p));
            p.updateInventory();
            p.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("activate") + ChatColor.RED + getlang("disable"));
            // Reset signals
            for (int i = 0; i < lastresetablesign.get(p).length; i++) {
                BlockState state = null;
                try {
                    state = p.getWorld().getBlockAt(lastresetablesign.get(p)[i]).getState();
                } catch (Exception ignored) {
                }
                if (state instanceof Sign) {
                    Sign sign = (Sign) state;
                    if (sign.getLine(2).split(" ")[0].equals("set")) {
                        sign.setLine(2, lastresetabletxt.get(p)[i]);
                        sign.update();
                    }
                }
            }
            lastresetablesign.remove(p);
            lastresetabletxt.remove(p);
        }
    }
}
