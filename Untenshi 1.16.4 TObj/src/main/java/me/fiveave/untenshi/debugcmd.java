package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static me.fiveave.untenshi.cmds.*;
import static me.fiveave.untenshi.main.getlang;
import static me.fiveave.untenshi.main.vehicle;

class debugcmd implements CommandExecutor, TabCompleter {

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (sender instanceof Player) {
                if (args.length >= 3) {
                    if (checkPerm((Player) sender, "uts.debug")) {
                        noPerm(sender);
                        return true;
                    }
                    utsvehicle lv = null;
                    try {
                        lv = vehicle.get(TrainProperties.get(args[0]).getHolder());
                    } catch (Exception ignored) {
                    }
                    if (lv != null) {
                        switch (args[1].toLowerCase()) {
                            case "get":
                                String retstr = "";
                                switch (args[2].toLowerCase()) {
                                    case "speed":
                                        retstr = String.valueOf(lv.getSpeed());
                                        break;
                                    case "mascon":
                                        retstr = String.valueOf(lv.getMascon());
                                        break;
                                    case "speedlimit":
                                        retstr = String.valueOf(lv.getSpeedlimit());
                                        break;
                                    case "signallimit":
                                        retstr = String.valueOf(lv.getSignallimit());
                                        break;
                                    case "atospeed":
                                        retstr = String.valueOf(lv.getAtospeed());
                                        break;
                                    case "atodest":
                                        retstr = locToString(lv.getAtodest());
                                        break;
                                    case "lastspsp":
                                        retstr = String.valueOf(lv.getLastspsp());
                                        break;
                                    case "lastspsign":
                                        retstr = locToString(lv.getLastspsign());
                                        break;
                                    case "lastsisp":
                                        retstr = String.valueOf(lv.getLastsisp());
                                        break;
                                    case "lastsisign":
                                        retstr = locToString(lv.getLastsisign());
                                        break;
                                    case "rslist":
                                        retstr = locListToString(lv.getRsposlist());
                                        break;
                                    case "ilposlist":
                                        retstr = locListToString(lv.getIlposlist());
                                        break;
                                    case "ilposoccupied":
                                        retstr = locListToString(lv.getIlposoccupied());
                                        break;
                                    case "ilpriority":
                                        retstr = String.valueOf(lv.getIlpriority());
                                        break;
                                    case "ilenterqueuetime":
                                        retstr = String.valueOf(lv.getIlenterqueuetime());
                                        break;
                                    case "atsforced":
                                        retstr = String.valueOf(lv.getAtsforced());
                                        break;
                                    case "atsping":
                                        retstr = String.valueOf(lv.getAtsping());
                                        break;
                                    case "atspnear":
                                        retstr = String.valueOf(lv.isAtspnear());
                                        break;
                                    case "ld":
                                        retstr = lv.getLd().getP().getName();
                                        break;
                                    default:
                                        generalMsg(sender, ChatColor.RED, getlang("argwrong"));
                                        break;
                                }
                                generalMsg(sender, ChatColor.GRAY, args[0] + ": " + ChatColor.YELLOW + args[2].toLowerCase() + ChatColor.WHITE + " = " + ChatColor.GREEN + retstr);
                                break;
                            case "set":
                                if (args.length >= 4) {
                                    try {
                                        switch (args[2].toLowerCase()) {
                                            case "speed":
                                                lv.setSpeed(Double.parseDouble(args[3]));
                                                break;
                                            case "mascon":
                                                lv.setMascon(Integer.parseInt(args[3]));
                                                break;
                                            case "speedlimit":
                                                lv.setSpeedlimit(Integer.parseInt(args[3]));
                                                break;
                                            case "signallimit":
                                                lv.setSignallimit(Integer.parseInt(args[3]));
                                                break;
                                            case "atospeed":
                                                lv.setAtospeed(Double.parseDouble(args[3]));
                                                break;
                                            case "atodest":
                                                int[] loc1 = new int[3];
                                                for (int i = 0; i < 3; i++) {
                                                    loc1[i] = Integer.parseInt(args[i + 3]);
                                                }
                                                lv.setAtodest(loc1);
                                                break;
                                            case "lastspsp":
                                                lv.setLastspsp(Integer.parseInt(args[3]));
                                                break;
                                            case "lastspsign":
                                                Location loc2 = new Location(lv.getSavedworld(), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
                                                lv.setLastspsign(loc2);
                                                break;
                                            case "lastsisp":
                                                lv.setLastsisp(Integer.parseInt(args[3]));
                                                break;
                                            case "lastsisign":
                                                Location loc3 = new Location(lv.getSavedworld(), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
                                                lv.setLastsisign(loc3);
                                                break;
                                            case "rslist":
                                            case "ilposlist":
                                            case "ilposoccupied":
                                            case "ilpriority":
                                            case "ilenterqueuetime":
                                            case "ld":
                                                generalMsg(sender, ChatColor.YELLOW, "Option is not available yet.");
                                                break;
                                            case "atsforced":
                                                lv.setAtsforced(Integer.parseInt(args[3]));
                                                break;
                                            case "atsping":
                                                lv.setAtsping(Integer.parseInt(args[3]));
                                                break;
                                            case "atspnear":
                                                lv.setAtspnear(Boolean.parseBoolean(args[3]));
                                                break;
                                            default:
                                                generalMsg(sender, ChatColor.RED, getlang("argwrong"));
                                                break;
                                        }
                                    } catch (Exception e) {
                                        generalMsg(sender, ChatColor.RED, getlang("argwrong"));
                                    }
                                } else {
                                    generalMsg(sender, ChatColor.RED, getlang("argwrong"));
                                }
                        }
                    }
                } else {
                    generalMsg(sender, ChatColor.RED, getlang("argwrong"));
                }
            } else {
                generalMsg(sender, ChatColor.RESET, getlang("playeronlycmd"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> ta = new ArrayList<>();
        List<String> result = new ArrayList<>();
        List<String> vehiclelist = new ArrayList<>();
        for (MinecartGroup mg : vehicle.keySet()) {
            vehiclelist.add(mg.getProperties().getTrainName());
        }
        switch (args.length) {
            case 1:
                ta.addAll(vehiclelist);
                break;
            case 2:
                ta.addAll(Arrays.asList("get", "set"));
                break;
            case 3:
                ta.addAll(Arrays.asList("speed", "mascon", "speedlimit", "signallimit", "atospeed", "atodest", "lastspsp", "lastspsign", "lastsisp", "lastsisign", "rslist", "ilposlist", "ilposoccupied", "ilpriority", "ilenterqueuetime", "atsforced", "atsping", "atspnear", "ld"));
                break;
        }
        for (String a : ta) {
            if (a.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                result.add(a);
            }
        }
        return result;
    }

    public String locToString(int[] loc) {
        String retstr = "null";
        try {
            for (int i = 0; i < 3; i++) {
                retstr = loc[0] + " " + loc[1] + " " + loc[2];
            }
        } catch (Exception ignored) {
        }
        return retstr;
    }

    public String locToString(Location loc) {
        String retstr = "null";
        try {
            for (int i = 0; i < 3; i++) {
                retstr = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
            }
        } catch (Exception ignored) {

        }
        return retstr;
    }

    public String locListToString(Location[] locs) {
        StringBuilder retstr = new StringBuilder();
        int i = 0;
        try {
            while (i < locs.length) {
                retstr.append(String.format("\n%s%d%s:%s %d %d %d", ChatColor.AQUA, i, ChatColor.WHITE, ChatColor.GREEN, locs[i].getBlockX(), locs[i].getBlockY(), locs[i].getBlockZ()));
                i++;
            }
        } catch (Exception e) {
            retstr.append("null");
        }
        return retstr.toString();
    }
}