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

import static me.fiveave.untenshi.events.*;
import static me.fiveave.untenshi.main.*;

class cmds implements CommandExecutor, TabCompleter {
    String helphead = ChatColor.GOLD + "/uts ";
    String helpformat = " " + ChatColor.WHITE + "-" + ChatColor.GREEN + " ";

    static void absentDriver(Player p) {
        driver.putIfAbsent(p, new untenshi(p, getPConfig().getBoolean("players." + p.getUniqueId() + ".freemode"), getPConfig().getBoolean("players." + p.getUniqueId() + ".allowatousage")));
    }

    static void helpSectionTitle(CommandSender sender, ChatColor color, String arg) {
        sender.sendMessage(pureutstitle + color + " ----- " + getlang("help" + arg + "title") + " -----");
    }

    static void generalMsg(CommandSender sender, ChatColor color, String s) {
        sender.sendMessage(utshead + color + s);
    }

    static FileConfiguration getPConfig() {
        return playerdata.dataconfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                generalMsg(sender, ChatColor.RED, getlang("playeronlycmd"));
                return true;
            }
            if (!sender.hasPermission("uts.main") && !sender.isOp()) {
                noPerm(sender);
                return true;
            }
            // Initialize from playerdata.yml
            Player sender2 = (Player) sender;
            // Initialize if none (not duplicating)
            absentDriver(sender2);
            untenshi ld = driver.get(sender2);
            // Force freemode false if perm not given
            if (!sender.hasPermission("uts.freemode")) {
                ld.setFreemode(false);
            }
            // Change arg to case-non-sensitive
            // For each arg need check length of arg to ensure safety
            if (args.length > 0) {
                label:
                switch (args[0].toLowerCase()) {
                    case "help":
                        if (args.length == 1) {
                            helpMsg(sender);
                            break;
                        }
                        if (args.length == 2) {
                            switch (args[1]) {
                                case "1":
                                    helpSectionTitle(sender, ChatColor.GREEN, args[1]);
                                    helpDesc(sender, getlang("cmdlist"));
                                    helpInfo(sender, "help <page>", getlang("help1a"));
                                    helpInfo(sender, "activate <true/false>", getlang("help1b"));
                                    helpInfo(sender, "atsconfirm/ac", getlang("help1e"));
                                    helpInfo(sender, "switchends/se", getlang("help1h"));
                                    helpInfo(sender, "pa <text>", getlang("help1i"));
                                    helpDesc(sender, getlang("help1g"));
                                    break;
                                case "2":
                                    helpSectionTitle(sender, ChatColor.LIGHT_PURPLE, args[1]);
                                    helpDesc(sender, getlang("cmdlist"));
                                    helpInfo(sender, "help <page>", getlang("help1a"));
                                    helpInfo(sender, "reload", getlang("help2a"));
                                    helpInfo(sender, "freemode <true/false>", getlang("help2c"));
                                    helpInfo(sender, "allowato <true/false>", getlang("help2d"));
                                    helpDesc(sender, getlang("help1g"));
                                    break;
                                case "3":
                                    helpSectionTitle(sender, ChatColor.GOLD, args[1]);
                                    sender.sendMessage(ChatColor.GREEN + getlang("help3a") + "\n" + ChatColor.YELLOW + getlang("help3b") + "\n" + getlang("help3c") + "\n" + getlang("help3d"));
                                    sender.sendMessage("\n" + ChatColor.RED + getlang("help3e") + "\n" + ChatColor.YELLOW + getlang("help3f") + "\n" + getlang("help3g") + "\n" + getlang("help3h"));
                                    sender.sendMessage(ChatColor.GOLD + "\n" + getlang("help3i") + "\n" + ChatColor.YELLOW + getlang("help3j") + "\n" + getlang("help3k"));
                                    break;
                                default:
                                    generalMsg(sender, ChatColor.YELLOW, getlang("pagenotexist"));
                                    break;
                            }
                        }
                        break;
                    case "activate":
                        if (args.length == 2) {
                            switch (args[1].toLowerCase()) {
                                case "true":
                                    if (ld.isPlaying()) {
                                        generalMsg(sender, ChatColor.YELLOW, getlang("activatedalready"));
                                        break label;
                                    }
                                    if (!sender2.isInsideVehicle()) {
                                        generalMsg(sender, ChatColor.YELLOW, getlang("sitincart"));
                                        break label;
                                    }
                                    // Get train
                                    Entity selcart = sender2.getVehicle();
                                    MinecartGroup mg = MinecartGroupStore.get(selcart);
                                    TrainProperties tprop = mg.getProperties();
                                    if (mg.size() == 1) {
                                        generalMsg(sender, ChatColor.YELLOW, getlang("sitincart"));
                                        break label;
                                    }
                                    // Detect owner
                                    if ((tprop.getOwners().size() == 1 && tprop.getOwners().contains(sender2.getName().toLowerCase()) || tprop.getOwners().isEmpty())) {
                                        // Train settings
                                        tprop.setSlowingDown(false);
                                        tprop.setOwner(sender2.getName(), true);
                                        tprop.setSpeedLimit(0);
                                        mg.setForwardForce(0);
                                        // Anti collision
                                        tprop.setCollision(tprop.getCollision().cloneAndSetMiscMode(CollisionMode.CANCEL));
                                        tprop.setCollision(tprop.getCollision().cloneAndSetPlayerMode(CollisionMode.CANCEL));
                                        // Set train group for player
                                        if (selcart instanceof Minecart) {
                                            //noinspection rawtypes
                                            MinecartMember mem = (MinecartMember) CommonEntity.get(selcart).getController();
                                            if (!selcart.getPassengers().isEmpty() && selcart.getPassengers().get(0) instanceof Player) {
                                                Bukkit.getScheduler().runTaskLater(plugin, () -> CartProperties.setEditing((Player) selcart.getPassengers().get(0), mem.getProperties()), interval);
                                            }
                                            ld.setTrain(mem.getGroup());
                                        }
                                        // Save inventory
                                        ld.setInv(sender2.getInventory().getContents());
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
                                        // Playing = true
                                        ld.setPlaying(true);
                                        motion.recursiveClock(ld);
                                        generalMsg(ld.getP(), ChatColor.YELLOW, getlang("trainset"));
                                        generalMsg(ld.getP(), ChatColor.YELLOW, getlang("activate") + ChatColor.GREEN + getlang("enable"));
                                    } else {
                                        generalMsg(ld.getP(), ChatColor.RED, getlang("notowner"));
                                    }
                                    break label;
                                case "false":
                                    if (ld.isPlaying()) {
                                        restoreinit(ld);
                                    } else {
                                        generalMsg(sender, ChatColor.YELLOW, getlang("deactivatedalready"));
                                    }
                                    break label;
                            }
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts activate <true/false>" + ChatColor.YELLOW + "]\n" + getlang("activateinfo1") + "\n" + getlang("activateinfo2"));
                        break;
                    case "atsconfirm":
                    case "ac":
                        if (ld.getSignallimit() != 0 && ld.isForcedbraking()) {
                            ld.setForcedbraking(false);
                            sender.sendMessage(utshead + ChatColor.GOLD + getlang("acsuccess"));
                        } else if (ld.getSignallimit() == 0) {
                            sender.sendMessage(utshead + ChatColor.RED + getlang("acfailed"));
                        } else {
                            sender.sendMessage(utshead + ChatColor.YELLOW + getlang("acnotneeded"));
                        }
                        break;
                    case "freemode":
                        if (cannotSetTrain(args, ld)) break;
                        if (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false")) {
                            ld.setFreemode(Boolean.parseBoolean(args[1].toLowerCase()));
                            sender.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("freemode") + (ld.isFreemode() ? ChatColor.GREEN + getlang("enable") : ChatColor.RED + getlang("disable")));
                            break;
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts freemode <true/false>" + ChatColor.YELLOW + "]\n" + getlang("freemodeinfo1") + "\n" + getlang("freemodeinfo2"));
                        break;
                    case "allowato":
                        if (cannotSetTrain(args, ld)) break;
                        if (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false")) {
                            ld.setAllowatousage(Boolean.parseBoolean(args[1].toLowerCase()));
                            sender.sendMessage(pureutstitle + ChatColor.YELLOW + getlang("ato") + (ld.isAllowatousage() ? ChatColor.GREEN + getlang("enable") : ChatColor.RED + getlang("disable")));
                            break;
                        }
                        sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + ChatColor.GOLD + "/uts allowato <true/false>" + ChatColor.YELLOW + "]\n" + getlang("atoinfo1") + "\n" + getlang("atoinfo2"));
                        break;
                    case "switchends":
                    case "se":
                        if (ld.isPlaying()) {
                            if (ld.getSpeed() == 0) {
                                Entity selcart = sender2.getVehicle();
                                MinecartGroup mg = MinecartGroupStore.get(selcart);
                                MinecartMember<?> mm = MinecartMemberStore.getFromEntity(selcart);
                                MinecartMember<?> mm2 = mm;
                                assert mm != null;
                                // Check if head or tail is not current cart and they are minecarts
                                if ((mg.head() != mm || mg.tail() != mm) && !ld.isFrozen()) {
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
                                        ld.setFrozen(true);
                                        mm.eject();
                                        MinecartMember<?> finalMm = mm2;
                                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                            ld.getP().teleport(finalMm.getEntity().getLocation());
                                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                finalMm.addPassengerForced(ld.getP());
                                                generalMsg(ld.getP(), ChatColor.YELLOW, getlang("sesuccess"));
                                                ld.setFrozen(false);
                                            }, interval);
                                        }, interval);
                                        break;
                                    }
                                }
                                generalMsg(sender, ChatColor.RED, getlang("sefailed"));
                            } else {
                                generalMsg(sender, ChatColor.YELLOW, getlang("seinmotion"));
                            }
                        } else {
                            generalMsg(sender, ChatColor.YELLOW, getlang("activatefirst"));
                        }
                        break;
                    case "pa":
                        if (checkPerm(sender2, "uts.pa")) break;
                        if (ld.isPlaying()) {
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
                                        // To keep & type \&
                                        s = s.replaceAll("\\\\&", "\\\\and");
                                        s = s.replaceAll("&", "§");
                                        s = s.replaceAll("\\\\and", "&");
                                        generalMsg(p, ChatColor.YELLOW, getlang("help1i") + ": " + s);
                                    } else {
                                        sender2.sendMessage(utshead + ChatColor.RED + getlang("panoempty"));
                                    }
                                }
                            }
                        } else {
                            generalMsg(sender, ChatColor.YELLOW, getlang("activatefirst"));
                        }
                        break;
                    case "reload":
                        if (checkPerm(sender2, "uts.reload")) break;
                        plugin.reloadConfig();
                        config = new abstractfile(plugin, "config.yml");
                        traindata = new abstractfile(plugin, "traindata.yml");
                        signalorder = new abstractfile(plugin, "signalorder.yml");
                        langdata = new abstractfile(plugin, "lang_" + plugin.getConfig().getString("lang") + ".yml");
                        sender.sendMessage(utshead + ChatColor.YELLOW + getlang("reloaded"));
                        break;
                    default:
                        generalMsg(sender, ChatColor.YELLOW, getlang("cmdnotexist"));
                        break;
                }
            } else {
                helpMsg(sender);
            }
            // Save in config
            getPConfig().set("players." + sender2.getUniqueId() + ".freemode", ld.isFreemode());
            getPConfig().set("players." + sender2.getUniqueId() + ".allowatousage", ld.isAllowatousage());
            playerdata.save();
        } catch (Exception e) {
            sender.sendMessage(utshead + ChatColor.RED + getlang("error"));
            e.printStackTrace();
        }
        return true;
    }

    private boolean cannotSetTrain(String[] args, untenshi ld) {
        return checkPerm(ld.getP(), "uts." + args[0].toLowerCase()) || reqDeactivate(ld) || args.length != 2;
    }

    private boolean reqDeactivate(untenshi ld) {
        if (ld.isPlaying()) {
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang("deactivatefirst"));
            return true;
        }
        return false;
    }

    private boolean checkPerm(Player sender2, String name) {
        if (!sender2.hasPermission(name)) {
            noPerm(sender2);
            return true;
        }
        return false;
    }

    void noPerm(CommandSender sender) {
        sender.sendMessage(utshead + ChatColor.RED + getlang("noperm"));
    }

    void helpDesc(CommandSender sender, String s) {
        sender.sendMessage(ChatColor.YELLOW + s);
    }

    void helpInfo(CommandSender sender, String s, String t) {
        sender.sendMessage(helphead + s + helpformat + t);
    }

    void helpMsg(CommandSender sender) {
        sender.sendMessage(pureutstitle + helphead + "help <page>" + helpformat + getlang("help1a"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> ta = new ArrayList<>();
        List<String> result = new ArrayList<>();
        int arglength = args.length;
        if (arglength == 1) {
            ta.addAll(Arrays.asList("help", "activate", "atsconfirm", "ac", "switchends", "se", "freemode", "reload", "pa", "allowato"));
            for (String a : ta) {
                if (a.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(a);
                }
            }
            return result;
        } else if (arglength == 2) {
            switch (args[0].toLowerCase()) {
                case "help":
                    ta.addAll(Arrays.asList("1", "2", "3"));
                    break;
                case "activate":
                case "freemode":
                case "allowato":
                    ta.addAll(Arrays.asList("true", "false"));
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