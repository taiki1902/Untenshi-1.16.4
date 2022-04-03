package me.fiveave.untenshi;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static me.fiveave.untenshi.events.*;
import static me.fiveave.untenshi.main.*;

public class cmds implements CommandExecutor, TabCompleter {
    protected String helphead = ChatColor.GOLD + "/uts ";
    protected String helpformat = " " + ChatColor.WHITE + "-" + ChatColor.GREEN + " ";
    protected String pDataInfo;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage(utshead + ChatColor.RED + getlang("playeronlycmd"));
                return true;
            }
            if (!sender.hasPermission("uts.main") && !sender.isOp()) {
                noPerm(sender);
                return true;
            }
            Player sender2 = (Player) sender;
            // Initialize from playerdata.yml
            pDataInfo = "players." + sender2.getUniqueId();
            if (pcontains(".traintype"))
                traintype.put(sender2, getPConfig().getString(pDataInfo + ".traintype"));
            if (pcontains(".freemode"))
                freemode.put(sender2, getPConfig().getBoolean(pDataInfo + ".freemode"));
            if (pcontains(".allowatousage"))
                allowatousage.put(sender2, getPConfig().getBoolean(pDataInfo + ".allowatousage"));
            // Initialize if none
            playing.putIfAbsent(sender2, false);
            speed.putIfAbsent(sender2, 0.0);
            points.putIfAbsent(sender2, 30);
            freemode.putIfAbsent(sender2, false);
            traintype.putIfAbsent(sender2, "local");
            signallimit.putIfAbsent(sender2, 360);
            dooropen.putIfAbsent(sender2, 0);
            fixstoppos.putIfAbsent(sender2, false);
            reqstopping.putIfAbsent(sender2, false);
            doordiropen.putIfAbsent(sender2, false);
            overrun.putIfAbsent(sender2, false);
            frozen.putIfAbsent(sender2, false);
            atsing.putIfAbsent(sender2, false);
            atsebing.putIfAbsent(sender2, false);
            allowatousage.putIfAbsent(sender2, false);
            atsping.putIfAbsent(sender2, false);
            atspnear.putIfAbsent(sender2, false);
            lastresetablesign.putIfAbsent(sender2, new Location[2]);
            lastresetabletxt.putIfAbsent(sender2, new String[2]);
            // Force freemode false if perm not given
            if (!sender.hasPermission("uts.freemode")) {
                freemode.put(sender2, false);
            }
            // Change arg to case-non-sensitive
            // For each arg need check length of arg to ensure safety
            if (args.length > 0) {
                label:
                switch (args[0].toLowerCase()) {
                    case "help":
                        if (args.length == 1) {
                            ta(sender, getlang("help1a"));
                            break;
                        }
                        if (args.length == 2) {
                            switch (args[1]) {
                                case "1":
                                    tta2(sender, ChatColor.GREEN, args[1]);
                                    tt(sender, getlang("cmdlist"));
                                    t(sender, "help <page>", getlang("help1a"));
                                    t(sender, "activate <true/false>", getlang("help1b"));
                                    t(sender, "atsconfirm/ac", getlang("help1e"));
                                    t(sender, "switchends/se", getlang("help1h"));
                                    t(sender, "pa <text>", getlang("help1i"));
                                    tt(sender, getlang("help1g"));
                                    break;
                                case "2":
                                    tta2(sender, ChatColor.LIGHT_PURPLE, args[1]);
                                    tt(sender, getlang("cmdlist"));
                                    t(sender, "help <page>", getlang("help1a"));
                                    t(sender, "reload", getlang("help2a"));
                                    t(sender, "traintype <local/hsr/lrt>", getlang("help2b"));
                                    t(sender, "freemode <true/false>", getlang("help2c"));
                                    t(sender, "allowato <true/false>", getlang("help2d"));
                                    tt(sender, getlang("help1g"));
                                    break;
                                case "3":
                                    tta2(sender, ChatColor.GOLD, args[1]);
                                    sender.sendMessage(ChatColor.GREEN + getlang("help3a") + "\n" + ChatColor.YELLOW + getlang("help3b") + "\n" + getlang("help3c") + "\n" + getlang("help3d"));
                                    sender.sendMessage("\n" + ChatColor.RED + getlang("help3e") + "\n" + ChatColor.YELLOW + getlang("help3f") + "\n" + getlang("help3g") + "\n" + getlang("help3h"));
                                    sender.sendMessage(ChatColor.GOLD + "\n" + getlang("help3i") + "\n" + ChatColor.YELLOW + getlang("help3j") + "\n" + getlang("help3k"));
                                    break;
                                case "4":
                                    tta2(sender, ChatColor.DARK_AQUA, args[1]);
                                    sender.sendMessage(ChatColor.GOLD + getlang("help4a") + "\n" + ChatColor.GREEN + getlang("help4b") + ChatColor.YELLOW + getlang("help4c") + "\n" + ChatColor.GREEN + getlang("help4f") + ChatColor.YELLOW + getlang("help4c1"));
                                    sender.sendMessage("\n" + ChatColor.GOLD + getlang("help4d") + "\n" + ChatColor.GREEN + getlang("help4b") + ChatColor.YELLOW + getlang("help4e") + "\n" + ChatColor.GREEN + getlang("help4f") + ChatColor.YELLOW + getlang("help4g"));
                                    sender.sendMessage("\n" + ChatColor.GOLD + getlang("help4h") + "\n" + ChatColor.GREEN + getlang("help4b") + ChatColor.YELLOW + getlang("help4i") + "\n" + getlang("help4j") + "\n" + getlang("help4k") + "\n" + getlang("help4l") + "\n" + ChatColor.GREEN + getlang("help4f") + ChatColor.YELLOW + getlang("help4m"));
                                    sender.sendMessage("\n" + ChatColor.GOLD + getlang("help4n"));
                                    sender.sendMessage("\n" + ChatColor.RED + "/ atosign: " + ChatColor.UNDERLINE + "Experimental 試驗性 試験的" + ChatColor.RED + " /");
                                    sender.sendMessage("\n" + ChatColor.GOLD + getlang("help4o") + "\n" + ChatColor.GREEN + getlang("help4b") + ChatColor.YELLOW + getlang("help4p") + "\n" + ChatColor.GREEN + getlang("help4f") + ChatColor.YELLOW + getlang("help4q"));
                                    break;
                                default:
                                    tta(sender, ChatColor.YELLOW, getlang("pagenotexist"));
                                    break;
                            }
                        }
                        break;
                    case "activate":
                        if (args.length == 2) {
                            switch (args[1].toLowerCase()) {
                                case "true":
                                    if (playing.get(sender)) {
                                        tta(sender, ChatColor.YELLOW, getlang("activatedalready"));
                                        break label;
                                    }
                                    if (!sender2.isInsideVehicle()) {
                                        tta(sender, ChatColor.YELLOW, getlang("sitincart"));
                                        break label;
                                    }
                                    // Get train
                                    Entity selcart = sender2.getVehicle();
                                    MinecartGroup mg = MinecartGroupStore.get(selcart);
                                    TrainProperties tprop = mg.getProperties();
                                    // Detect owner
                                    if ((tprop.getOwners().size() == 1 && tprop.getOwners().contains(sender2.getName().toLowerCase()) || tprop.getOwners().isEmpty())) {
                                        // Save inventory
                                        inv.put(sender2, sender2.getInventory().getContents());
                                        // Clear other slots
                                        for (int i = 0; i < 41; i++) {
                                            sender2.getInventory().setItem(i, new ItemStack(Material.AIR));
                                        }
                                        // Set wands in place
                                        sender2.getInventory().setItem(0, upWand());
                                        sender2.getInventory().setItem(1, nWand());
                                        sender2.getInventory().setItem(2, downWand());
                                        sender2.getInventory().setItem(6, ebButton());
                                        sender2.getInventory().setItem(7, sbLever());
                                        sender2.getInventory().setItem(8, doorButton());
                                        // Train settings
                                        tprop.setSlowingDown(false);
                                        tprop.setSpeedLimit(0);
                                        tprop.setOwner(sender2.getName(), true);
                                        mg.setForwardForce(0);
                                        // Anti collision
                                        tprop.setCollision(tprop.getCollision().cloneAndSetMiscMode(CollisionMode.CANCEL));
                                        tprop.setCollision(tprop.getCollision().cloneAndSetPlayerMode(CollisionMode.CANCEL));
                                        // Reset values and init
                                        dooropen.put(sender2, 0);
                                        doordiropen.put(sender2, false);
                                        doorconfirm.put(sender2, false);
                                        fixstoppos.put(sender2, false);
                                        staeb.put(sender2, false);
                                        staaccel.put(sender2, false);
                                        speedlimit.put(sender2, 360);
                                        signallimit.put(sender2, 360);
                                        mascon.put(sender2, -9);
                                        current.put(sender2, -480.0);
                                        points.put(sender2, 30);
                                        atsing.put(sender2, false);
                                        atsebing.put(sender2, false);
                                        atsping.put(sender2, false);
                                        atspnear.put(sender2, false);
                                        overrun.put(sender2, false);
                                        lasty.put(sender2, Objects.requireNonNull(sender2.getVehicle()).getLocation().getY());
                                        deductdelay.put(sender2, 49);
                                        signaltype.put(sender2, "ats");
                                        tta(sender, ChatColor.YELLOW, getlang("trainset"));
                                        // Playing = true
                                        playing.put(sender2, true);
                                        motion.recursion1(sender);
                                        // Set train group for player
                                        if (selcart instanceof Minecart) {
                                            //noinspection rawtypes
                                            MinecartMember mem = (MinecartMember) CommonEntity.get(selcart).getController();
                                            if (!selcart.getPassengers().isEmpty() && selcart.getPassengers().get(0) instanceof Player) {
                                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                    CartProperties.setEditing((Player) selcart.getPassengers().get(0), mem.getProperties());
                                                    train.put(sender2, mem.getGroup());
                                                }, 1);
                                            }
                                        }
                                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("activate") + ChatColor.GREEN + getlang("enable"));
                                    } else {
                                        tta(sender, ChatColor.RED, getlang("notowner"));
                                    }
                                    break label;
                                case "false":
                                    if (playing.get(sender)) {
                                        restoreinit(sender2);
                                    } else {
                                        tta(sender, ChatColor.YELLOW, getlang("deactivatedalready"));
                                    }
                                    break label;
                            }
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts activate <true/false>" + ChatColor.YELLOW + "]\n" + getlang("activateinfo1") + "\n" + getlang("activateinfo2"));
                        break;
                    case "atsconfirm":
                    case "ac":
                        if (!signallimit.get(sender).equals(0) && ((atsing.get(sender) && !atsebing.get(sender)) || (atsebing.get(sender) && speed.get(sender) <= 0))) {
                            atsing.put(sender2, false);
                            atsebing.put(sender2, false);
                            atsdelay.put(sender2, -1);
                            sender.sendMessage(utshead + ChatColor.GOLD + getlang("acsuccess"));
                        } else if (signallimit.get(sender).equals(0) || (atsebing.get(sender) && speed.get(sender) > 0)) {
                            sender.sendMessage(utshead + ChatColor.RED + getlang("acfailed"));
                        } else {
                            sender.sendMessage(utshead + ChatColor.YELLOW + getlang("acnotneeded"));
                        }
                        break;
                    case "traintype":
                        if (checkperm(sender2, "uts.traintype")) break;
                        if (args.length == 2) {
                            if (!playing.get(sender)) {
                                switch (args[1].toLowerCase()) {
                                    case "local":
                                    case "hsr":
                                    case "lrt":
                                        traintype.put(sender2, args[1].toLowerCase());
                                        tta(sender, ChatColor.YELLOW, getlang(args[1].toLowerCase() + "trainaccel"));
                                        break label;
                                }
                            } else {
                                tta(sender, ChatColor.YELLOW, getlang("deactivatefirst"));
                                break;
                            }
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts traintype <local/hsr/lrt>" + ChatColor.YELLOW + "]\n" + getlang("traintypeinfo1") + "\n" + getlang("traintypeinfo2") + "\n" + getlang("traintypeinfo3"));
                        break;
                    case "freemode":
                        if (checkperm(sender2, "uts.freemode")) break;
                        if (args.length == 2 && (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
                            if (!playing.get(sender)) {
                                freemode.put(sender2, Boolean.valueOf(args[1].toLowerCase()));
                                sender.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("freemode") + (freemode.get(sender2) ? ChatColor.GREEN + getlang("enable") : ChatColor.RED + getlang("disable")));
                            } else {
                                tta(sender, ChatColor.YELLOW, getlang("deactivatefirst"));
                            }
                            break;
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts freemode <true/false>" + ChatColor.YELLOW + "]\n" + getlang("freemodeinfo1") + "\n" + getlang("freemodeinfo2"));
                        break;
                    case "allowato":
                        if (checkperm(sender2, "uts.allowato")) break;
                        if (args.length == 2 && (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
                            if (!playing.get(sender)) {
                                allowatousage.put(sender2, Boolean.valueOf(args[1].toLowerCase()));
                                sender.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("ato") + (allowatousage.get(sender) ? ChatColor.GREEN + getlang("enable") : ChatColor.RED + getlang("disable")));
                            } else {
                                tta(sender, ChatColor.YELLOW, getlang("deactivatefirst"));
                            }
                            break;
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts allowato <true/false>" + ChatColor.YELLOW + "]\n" + getlang("atoinfo1") + "\n" + getlang("atoinfo2"));
                        break;
                    case "switchends":
                    case "se":
                        if (playing.get(sender)) {
                            if (speed.get(sender).equals(0.0)) {
                                Entity selcart = sender2.getVehicle();
                                MinecartGroup mg = MinecartGroupStore.get(selcart);
                                MinecartMember<?> mm = MinecartMemberStore.getFromEntity(selcart);
                                MinecartMember<?> mm2 = mm;
                                assert mm != null;
                                // Check if head or tail is not current cart and they are minecarts
                                if ((mg.head() != mm || mg.tail() != mm) && !frozen.get(sender)) {
                                    // Must clear passenger in cart, or else player will bug out
                                    if (mg.head() != mm) {
                                        mm2 = mg.head();
                                        mm2.eject();
                                    } else if (mg.tail() != mm) {
                                        mm2 = mg.tail();
                                        mm2.eject();
                                    }
                                    if (mm2.getEntity().getEntity() instanceof RideableMinecart) {
                                        // MUST wait after eject, and teleport, and finally enter, or else will bug
                                        frozen.put(sender2, true);
                                        mm.eject();
                                        MinecartMember<?> finalMm = mm2;
                                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                            sender2.teleport(finalMm.getEntity().getLocation());
                                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                finalMm.addPassengerForced(sender2);
                                                sender2.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("sesuccess"));
                                                frozen.put(sender2, false);
                                            }, 2);
                                        }, 2);
                                        break;
                                    }
                                }
                                sender.sendMessage(pureutstitle + ChatColor.RED + getlang("sefailed"));
                            } else {
                                tta(sender, ChatColor.YELLOW, getlang("seinmotion"));
                            }
                        } else {
                            tta(sender, ChatColor.YELLOW, getlang("activatefirst"));
                        }
                        break;
                    case "pa":
                        if (checkperm(sender2, "uts.pa")) break;
                        if (playing.get(sender)) {
                            MinecartGroup mg = MinecartGroupStore.get(sender2.getVehicle());
                            //noinspection rawtypes
                            for (MinecartMember mm : mg) {
                                if (!mm.getEntity().getPassengers().isEmpty()) {
                                    Player p = (Player) mm.getEntity().getPassengers().get(0);
                                    String s;
                                    int arglength = args.length;
                                    if (arglength > 1 && args[1] != null) {
                                        StringBuilder sBuilder = null;
                                        for (int i = 1; i < arglength; i++) {
                                            if (sBuilder != null) {
                                                sBuilder.append(" ").append(args[i]);
                                            } else {
                                                sBuilder = new StringBuilder(args[i]);
                                            }
                                        }
                                        s = sBuilder.toString();
                                        s = s.replaceAll("\\\\&", "\\\\and");
                                        s = s.replaceAll("&", "§");
                                        s = s.replaceAll("\\\\and", "&");
                                        p.sendMessage(utshead + ChatColor.YELLOW + getlang("help1i") + ": " + s);
                                    } else {
                                        sender2.sendMessage(utshead + ChatColor.RED + getlang("panoempty"));
                                    }
                                }
                            }
                        } else {
                            tta(sender, ChatColor.YELLOW, getlang("activatefirst"));
                        }
                        break;
                    case "reload":
                        if (checkperm(sender2, "uts.reload")) break;
                        plugin.reloadConfig();
                        config = new abstractfile(plugin, "config.yml");
                        traindata = new abstractfile(plugin, "traindata.yml");
                        langdata = new abstractfile(plugin, "lang_" + plugin.getConfig().getString("lang") + ".yml");
                        sender.sendMessage(utshead + ChatColor.YELLOW + getlang("reloaded"));
                        break;
                    default:
                        tta(sender, ChatColor.YELLOW, getlang("cmdnotexist"));
                        break;
                }
            } else {
                ta(sender, getlang("help1a"));
            }
            // Save in config
            getPConfig().set(pDataInfo + ".traintype", traintype.get(sender2));
            getPConfig().set(pDataInfo + ".freemode", freemode.get(sender2));
            getPConfig().set(pDataInfo + ".allowatousage", allowatousage.get(sender2));
            playerdata.save();

        } catch (Exception e) {
            sender.sendMessage(utshead + ChatColor.RED + getlang("error"));
            e.printStackTrace();
        }
        return true;
    }

    private boolean checkperm(Player sender2, String name) {
        if (!sender2.hasPermission(name)) {
            noPerm(sender2);
            return true;
        }
        return false;
    }

    // ATO Stop Time Countdown
    static void atodepartcountdown(Player p) {
        if (playing.get(p)) {
            if (atostoptime.containsKey(p)) {
                if (atostoptime.get(p) > 0) {
                    p.sendMessage(utshead + ChatColor.YELLOW + getlang("door") + ChatColor.GOLD + "..." + atostoptime.get(p));
                    atostoptime.put(p, atostoptime.get(p) - 1);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> atodepartcountdown(p), 20);
                } else {
                    doorControls(p, false);
                    // Reset values in order to depart
                    atostoptime.remove(p);
                    atodest.remove(p);
                    atospeed.remove(p);
                    atodoorcloseddepart(p);
                }
            }
        }
    }

    private static void atodoorcloseddepart(Player p) {
        if (playing.get(p)) {
            // Wait doors fully closed then depart
            if (dooropen.get(p).equals(0) && doorconfirm.get(p)) {
                mascon.put(p, 1);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> atodoorcloseddepart(p), 2);
            }
        }
    }

    // Simplify
    protected boolean pcontains(String s) {
        return getPConfig().contains(pDataInfo + s);
    }

    protected void noPerm(CommandSender sender) {
        sender.sendMessage(utshead + ChatColor.RED + getlang("noperm"));
    }

    protected static void tta2(CommandSender sender, ChatColor color, String arg) {
        sender.sendMessage(pureutstitle + color + " ----- " + getlang("help" + arg + "title") + " -----");
    }

    protected static void tta(CommandSender sender, ChatColor yellow, String s) {
        sender.sendMessage(pureutstitle + yellow + s);
    }

    protected void tt(CommandSender sender, String s) {
        sender.sendMessage(ChatColor.YELLOW + s);
    }

    protected void t(CommandSender sender, String s, String t) {
        sender.sendMessage(helphead + s + helpformat + t);
    }

    protected void ta(CommandSender sender, String t) {
        sender.sendMessage(pureutstitle + helphead + "help <page>" + helpformat + t);
    }

    protected FileConfiguration getPConfig() {
        return playerdata.dataconfig;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> ta = new ArrayList<>();
        List<String> result = new ArrayList<>();
        int arglength = args.length;
        if (arglength == 1) {
            ta.addAll(Arrays.asList("help", "activate", "atsconfirm", "ac", "switchends", "se", "traintype", "freemode", "reload", "pa", "allowato"));
            for (String a : ta) {
                if (a.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(a);
                }
            }
            return result;
        } else if (arglength == 2) {
            switch (args[0].toLowerCase()) {
                case "help":
                    ta.addAll(Arrays.asList("1", "2", "3", "4"));
                    break;
                case "activate":
                case "freemode":
                case "allowato":
                    ta.addAll(Arrays.asList("true", "false"));
                    break;
                case "traintype":
                    ta.addAll(Arrays.asList("local", "hsr", "lrt"));
                    break;
                default:
                    ta.add("");
                    break;
            }
            for (String a : ta) {
                if (a.toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(a);
                }
            }
            return result;
            // Stop spamming player names
        } else if (arglength > 2) {
            result.add("");
            return result;
        }
        return null;
    }
}