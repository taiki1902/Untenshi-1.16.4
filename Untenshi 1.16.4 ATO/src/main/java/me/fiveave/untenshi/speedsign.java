package me.fiveave.untenshi;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.List;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signImproper;

class speedsign extends SignAction {

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
                    if (playing.containsKey(p) && playing.get(p)) {
                        // Speed limit set
                        if (!cartevent.getLine(2).equals("warn")) {
                            int intspeed = parseInt(speedsign);
                            if (intspeed <= maxspeed && intspeed >= 0 && Math.floorMod(intspeed, 5) == 0) {
                                speedlimit.put(p, intspeed);
                                generalMsg(p, ChatColor.YELLOW, getlang("speedlimitset") + (intspeed == maxspeed ? ChatColor.GREEN + getlang("nolimit") : speedlimit.get(p) + " km/h"));
                                if (parseInt(speedsign) != 0) {
                                    lastspsign.remove(p);
                                    lastspsp.remove(p);
                                }
                            } else {
                                signImproper(cartevent, p);
                            }
                        }
                        // Speed limit warn
                        else {
                            try {
                                Sign warn = getSign(cartevent);
                                if (warn != null) {
                                    lastspsign.put(p, warn.getLocation());
                                    int warnsp = parseInt(warn.getLine(2));
                                    lastspsp.put(p, warnsp);
                                    if (warnsp < maxspeed) {
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

    static Sign getSign(SignActionEvent cartevent) {
        if (cartevent.getWorld().getBlockAt(getLoc(cartevent, 0), getLoc(cartevent, 1), getLoc(cartevent, 2)).getState() instanceof Sign) {
            return (Sign) cartevent.getWorld().getBlockAt(getLoc(cartevent, 0), getLoc(cartevent, 1), getLoc(cartevent, 2)).getState();
        } else {
            return null;
        }
    }

    static int getLoc(SignActionEvent cartevent, int i) {
        return parseInt(cartevent.getLine(3).split(" ")[i]);
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
}