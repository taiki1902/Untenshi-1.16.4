package me.fiveave.untenshi;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.*;
import static me.fiveave.untenshi.speedsign.getFullLoc;
import static me.fiveave.untenshi.speedsign.getSignFromLoc;

class signalcmd implements CommandExecutor, TabCompleter {

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (sender instanceof BlockCommandSender) {
                int signalspeed = args[3].equals("sign") ? parseInt(args[5]) : 0;
                // Anti returning wrong arguments
                String e;
                String inputpos = args[0] + " " + args[1] + " " + args[2];
                if (args.length == 4) {
                    e = args[3];
                } else if (args.length == 6) {
                    e = args[3] + " " + args[4] + " " + args[5];
                } else {
                    e = null;
                }
                // Main content starts here
                if (signalspeed > maxspeed) {
                    sender.sendMessage(getSpeedMax());
                    return true;
                }
                if (signalspeed < 0) {
                    sender.sendMessage(utshead + getlang("speedmin0"));
                    return true;
                }
                if (Math.floorMod(signalspeed, 5) != 0) {
                    sender.sendMessage(utshead + getlang("speeddiv5"));
                    return true;
                }
                assert e != null;
                if ((l1(e).equals("warn") || l1(e).equals("sign"))) {
                    String signalmsg;
                    switch (args[3]) {
                        // Signal speed limit warn
                        case "warn":
                            Sign warn = getSignFromLoc(getFullLoc(((BlockCommandSender) sender).getBlock().getWorld(), inputpos));
                            if (warn != null && warn.getLine(1).equals("signalsign")) {
                                // lastsisign and lastsisp are for detecting signal change
                                String warnsi = warn.getLine(2).split(" ")[1];
                                String warnsp = warn.getLine(2).split(" ")[2];
                                signalmsg = signalName(warnsi);
                                if (signalmsg.equals("")) {
                                    sender.sendMessage(utshead + ChatColor.RED + getlang("signaltypewrong"));
                                }
                                String showspeed = parseInt(warnsp) >= maxspeed ? getlang("nolimit") : warnsp + " km/h";
                                sender.sendMessage(utshead + ChatColor.YELLOW + getlang("signalwarn") + signalmsg + ChatColor.GRAY + " " + showspeed);
                            }
                            break;
                        // Set line 4 of sign at (line 3 of this sign) to turn signal
                        case "sign":
                            if (isSignalType(args[4])) {
                                Sign sign = getSignFromLoc(getFullLoc(((BlockCommandSender) sender).getBlock().getWorld(), inputpos));
                                if (sign != null) {
                                    updateSignals(sign, "set " + args[4] + " " + args[5]);
                                    sender.sendMessage(utshead + getlang("signalsignchange") + " (" + args[4] + " " + args[5] + ")");
                                }
                                break;
                            }
                        default:
                            sender.sendMessage(utshead + getlang("signalargwrong"));
                            break;
                    }
                }
            } else {
                sender.sendMessage(utshead + getlang("cmdblkonlycmd"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    // Simplify

    // l[n]: "n"th split text in line 3
    String l1(String e) {
        return e.toLowerCase().split(" ")[0];
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> ta = new ArrayList<>();
        List<String> result = new ArrayList<>();
        if (sender instanceof Player) {
            Player p = (Player) sender;
            Block block = p.getTargetBlock(Collections.singleton(Material.AIR), 5);
            if (block.getType() != Material.COMMAND_BLOCK && block.getType() != Material.CHAIN_COMMAND_BLOCK && block.getType() != Material.REPEATING_COMMAND_BLOCK) {
                switch (args.length) {
                    case 1:
                        ta.addAll(Arrays.asList(block.getX() + " ", block.getX() + " " + block.getY(), block.getX() + " " + block.getY() + " " + block.getZ()));
                        break;
                    case 2:
                        ta.addAll(Arrays.asList(block.getY() + " ", block.getY() + " " + block.getZ()));
                        break;
                    case 3:
                        ta.add(block.getZ() + " ");
                        break;
                }
            }
        }
        if (args.length == 4) {
            ta.addAll(Arrays.asList("warn", "sign"));
        } else if (args.length == 5 && args[3].equals("sign")) {
            ta.addAll(Arrays.asList("r", "yy", "y", "yg", "g", "atc"));
        } else {
            ta.add("");
        }
        for (String a : ta) {
            if (a.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                result.add(a);
            }
        }
        return result;
    }
}