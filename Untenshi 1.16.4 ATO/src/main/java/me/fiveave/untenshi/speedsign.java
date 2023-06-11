package me.fiveave.untenshi;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

import java.util.List;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.absentDriver;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signImproper;

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

    @SuppressWarnings("rawtypes")
    @Override
    public void execute(SignActionEvent cartevent) {
        if (cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && cartevent.hasRailedMember() && cartevent.isPowered()) {
            String speedsign = cartevent.getLine(2);
            for (MinecartMember cart : cartevent.getMembers()) {
                // For each passenger on cart
                CommonEntity cart2 = cart.getEntity();
                List cartpassengers = cart2.getPassengers();
                for (Object cartobj : cartpassengers) {
                    Player p = (Player) cartobj;
                    absentDriver(p);
                    untenshi ld = driver.get(p);
                    if (ld.isPlaying()) {
                        // Speed limit set
                        if (!cartevent.getLine(2).equals("warn")) {
                            int intspeed = parseInt(speedsign);
                            if (intspeed <= maxspeed && intspeed >= 0 && Math.floorMod(intspeed, 5) == 0) {
                                ld.setSpeedlimit(intspeed);
                                // ATC signal and speed limit min value
                                if (ld.getSafetysystype().equals("atc")) {
                                    intspeed = Math.min(ld.getSignallimit(), ld.getSpeedlimit());
                                    String temp = intspeed >= maxspeed ? getlang("nolimit") : intspeed + " km/h";
                                    generalMsg(p, ChatColor.YELLOW, getlang("signalset") + ChatColor.GOLD + "ATC" + ChatColor.GRAY + " " + temp);
                                } else {
                                    generalMsg(p, ChatColor.YELLOW, getlang("speedlimitset") + (intspeed == maxspeed ? ChatColor.GREEN + getlang("nolimit") : intspeed + " km/h"));
                                }
                                if (parseInt(speedsign) != 0) {
                                    ld.setLastspsign(null);
                                    ld.setLastspsp(maxspeed);
                                }
                            } else {
                                signImproper(cartevent, p);
                            }
                        }
                        // Speed limit warn
                        else {
                            try {
                                Sign warn = getSignFromLoc(getFullLoc(cartevent.getWorld(), cartevent.getLine(3)));
                                if (warn != null) {
                                    ld.setLastspsign(warn.getLocation());
                                    int warnsp = parseInt(warn.getLine(2));
                                    ld.setLastspsp(warnsp);
                                    if (warnsp < maxspeed) {
                                        // ATC signal and speed limit min value
                                        if (ld.getSafetysystype().equals("atc")) {
                                            warnsp = Math.min(Math.min(ld.getLastsisp(), ld.getLastspsp()), ld.getSignallimit());
                                        }
                                        generalMsg(p, ChatColor.YELLOW, getlang("speedlimitwarn") + warnsp + " km/h");
                                    } else {
                                        signImproper(cartevent, p);
                                    }
                                } else {
                                    signImproper(cartevent, p);
                                }
                            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                                signImproper(cartevent, p);
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent e) {
        if (noPerm(e)) return true;
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
                if (intspeed <= 0) {
                    p.sendMessage(ChatColor.RED + getlang("speedpositive"));
                    e.setCancelled(true);
                }
                if (Math.floorMod(intspeed, 5) != 0) {
                    p.sendMessage(ChatColor.RED + getlang("speeddiv5"));
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
            e.getPlayer().sendMessage(ChatColor.RED + getlang("signimproper"));
            e.setCancelled(true);
            exception.printStackTrace();
        }
        return true;
    }
}