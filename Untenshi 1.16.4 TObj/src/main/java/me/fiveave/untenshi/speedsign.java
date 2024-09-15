package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;

class speedsign extends SignAction {

    static Location getFullLoc(World world, String loctext) {
        return new Location(world, getLoc(loctext, 0), getLoc(loctext, 1), getLoc(loctext, 2));
    }

    static Sign getSignFromLoc(Location loc) {
        BlockState bl = loc.getBlock().getState();
        return bl instanceof Sign ? (Sign) bl : null;
    }

    static Chest getChestFromLoc(Location loc) {
        BlockState bl = loc.getBlock().getState();
        return bl instanceof Chest ? (Chest) bl : null;
    }

    static int[] getSignToRailOffset(Location loc, World w) {
        int[] blkoffset = new int[3];
        if (w.getBlockAt(loc) instanceof Sign) {
            Sign sign = (Sign) w.getBlockAt(loc);
            if (sign instanceof WallSign) {
                blkoffset[1] = 1;
                WallSign ws = (WallSign) sign;
                switch (String.valueOf(ws.getFacing())) {
                    case "NORTH":
                        blkoffset[2] = 1;
                        break;
                    case "SOUTH":
                        blkoffset[2] = -1;
                        break;
                    case "WEST":
                        blkoffset[0] = 1;
                        break;
                    case "EAST":
                        blkoffset[0] = -1;
                        break;
                }
            } else {
                blkoffset[1] = 2;
            }
            while (!(sign.getWorld().getBlockAt(blkoffset[0], blkoffset[1], blkoffset[2]) instanceof Rail)) {
                blkoffset[1]++;
            }
        }
        return blkoffset;
    }

    static void signImproper(SignActionEvent cartevent, utsdriver ld) {
        String s = utshead + ChatColor.RED + getLang("signimproper") + " (" + cartevent.getLocation().getBlockX() + " " + cartevent.getLocation().getBlockY() + " " + cartevent.getLocation().getBlockZ() + ")";
        if (ld != null && ld.getP() != null) {
            ld.getP().sendMessage(s);
        }
        Bukkit.getConsoleSender().sendMessage(s);
    }

    static int getLoc(String str, int i) {
        return parseInt(str.split(" ")[i]);
    }

    // l[n]: "n"th split text in line 3
    static String l1(SignActionEvent e) {
        return e.getLine(2).toLowerCase().split(" ")[0];
    }

    static String l2(SignActionEvent e) {
        return e.getLine(2).toLowerCase().split(" ")[1];
    }

    static String l3(SignActionEvent e) {
        return e.getLine(2).split(" ")[2];
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("speedsign");
    }

    @Override
    public void execute(SignActionEvent cartevent) {
        if (cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && cartevent.hasRailedMember() && cartevent.isPowered()) {
            MinecartGroup mg = cartevent.getGroup();
            String speedsign = cartevent.getLine(2);
            utsvehicle lv = vehicle.get(mg);
            if (lv != null)
            // Speed limit set
            {
                if (!cartevent.getLine(2).equals("warn")) {
                    try {
                        int intspeed = parseInt(speedsign);
                        if (intspeed <= maxspeed && intspeed >= 0 && Math.floorMod(intspeed, 5) == 0) {
                            lv.setSpeedlimit(intspeed);
                            // ATC signal and speed limit min value
                            if (lv.getSafetysystype().equals("atc")) {
                                intspeed = Math.min(lv.getSignallimit(), lv.getSpeedlimit());
                                String temp = intspeed >= maxspeed ? getLang("speedlimit_del") : intspeed + " km/h";
                                generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("signal_set") + " " + ChatColor.GOLD + "ATC" + ChatColor.GRAY + " " + temp);
                            } else {
                                generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("speedlimit_set") + " " + (intspeed == maxspeed ? ChatColor.GREEN + getLang("speedlimit_del") : intspeed + " km/h"));
                            }
                            if (parseInt(speedsign) != 0) {
                                lv.setLastspsign(null);
                                lv.setLastspsp(maxspeed);
                            }
                        } else {
                            signImproper(cartevent, lv.getLd());
                        }
                    } catch (NumberFormatException e) {
                        signImproper(cartevent, lv.getLd());
                    }
                }
                // Speed limit warn
                else {
                    try {
                        Sign warn = getSignFromLoc(getFullLoc(cartevent.getWorld(), cartevent.getLine(3)));
                        if (warn != null) {
                            lv.setLastspsign(warn.getLocation());
                            int warnsp = parseInt(warn.getLine(2));
                            lv.setLastspsp(warnsp);
                            if (warnsp < maxspeed) {
                                // ATC signal and speed limit min value
                                if (lv.getSafetysystype().equals("atc")) {
                                    warnsp = Math.min(Math.min(lv.getLastsisp(), lv.getLastspsp()), lv.getSignallimit());
                                    generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("signal_warn") + " " + ChatColor.GOLD + "ATC" + ChatColor.GRAY + " " + warnsp + " km/h");
                                } else {
                                    generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("speedlimit_warn") + " " + warnsp + " km/h");
                                }
                            } else {
                                signImproper(cartevent, lv.getLd());
                            }
                        } else {
                            signImproper(cartevent, lv.getLd());
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        signImproper(cartevent, lv.getLd());
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent e) {
        if (noSignPerm(e)) return true;
        Player p = e.getPlayer();
        try {
            int intspeed;
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "Speed limit sign");
            if (!e.getLine(2).equals("warn")) {
                intspeed = parseInt(e.getLine(2));
                if (intspeed > maxspeed) {
                    p.sendMessage(getSpeedMax());
                    e.setCancelled(true);
                }
                if (intspeed <= 0 || Math.floorMod(intspeed, 5) != 0) {
                    p.sendMessage(ChatColor.RED + getLang("argwrong"));
                    e.setCancelled(true);
                }
                opt.setDescription("set speed limit for train");
            } else {
                String[] temp = e.getLine(3).split(" ");
                parseInt(temp[0]);
                parseInt(temp[1]);
                parseInt(temp[2]);
                opt.setDescription("set speed limit warning for train");
            }
            return opt.handle(e.getPlayer());
        } catch (Exception exception) {
            p.sendMessage(ChatColor.RED + getLang("signimproper"));
            e.setCancelled(true);
        }
        return true;
    }
}