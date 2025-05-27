package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.Arrays;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.stoppos.curveRailPosFix;
import static me.fiveave.untenshi.utsvehicle.initVehicle;

class atosign extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("atosign");
    }

    @Override
    public void execute(SignActionEvent cartevent) {
        try {
            if (cartevent.hasRailedMember() && cartevent.isPowered()) {
                // For each passenger on cart
                MinecartGroup mg = cartevent.getGroup();
                MinecartMember<?> mm = cartevent.getMember();
                utsvehicle lv = vehicle.get(mg);
                if (lv == null && cartevent.getLine(2).equals("reg")) {
                    initVehicle(mg);
                    utsvehicle lv2 = vehicle.get(mg);
                    lv2.setBrake(8);
                    lv2.setMascon(0);
                } else if (lv != null && lv.getTrain() != null && (lv.getLd() == null || lv.getLd().isAllowatousage())) {
                    switch (cartevent.getLine(2)) {
                        case "reg":
                            break;
                        case "stoptime":
                            if (cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
                                int val = parseInt(cartevent.getLine(3));
                                lv.setAtoautodep(val >= 0);
                                lv.setAtostoptime(Math.abs(val));
                                generalMsg(lv.getLd(), ChatColor.GOLD, getLang("ato_detectstoptime"));
                            }
                            break;
                        case "dir":
                            // Only activate if train is stopped
                            if (lv.getSpeed() == 0) {
                                BlockFace bf = BlockFace.valueOf(cartevent.getLine(3).toUpperCase());
                                if (mm.getDirection().getOppositeFace().equals(bf)) {
                                    mg.reverse();
                                    lv.setDriverseat(mg.head());
                                    generalMsg(lv.getLd(), ChatColor.GOLD, getLang("dir_info") + " " + getLang("dir_" + mg.head().getDirection().toString().toLowerCase()));
                                    cartevent.setLevers(true);
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> cartevent.setLevers(false), 4);
                                }
                            }
                            break;
                        default:
                            // Only activate if train is not overrun
                            if (!lv.isOverrun() && cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
                                double[] loc = new double[3];
                                String[] sloc = cartevent.getLine(3).split(" ");
                                for (int a = 0; a <= 2; a++) {
                                    loc[a] = Integer.parseInt(sloc[a]);
                                }
                                lv.setOverrun(false);
                                double val = Double.parseDouble(cartevent.getLine(2));
                                // Direct or indirect pattern?
                                lv.setAtopisdirect(val < 0);
                                lv.setAtospeed(Math.abs(val));
                                curveRailPosFix(lv, loc);
                                lv.setAtodest(new Location(cartevent.getWorld(), loc[0], loc[1], loc[2]));
                                generalMsg(lv.getLd(), ChatColor.GOLD, getLang("ato_detectpattern"));
                                break;
                            }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean build(SignChangeActionEvent e) {
        if (noSignPerm(e)) return true;
        Player p = e.getPlayer();
        try {
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "ATO sign");
            switch (e.getLine(2)) {
                case "stoptime":
                    int stoptimeval = parseInt(e.getLine(3));
                    if (stoptimeval >= 1) {
                        opt.setDescription("set ATO station stopping time for train, train departs automatically after doors close");
                    } else if (Math.abs(stoptimeval) >= 1) {
                        opt.setDescription("set ATO station stopping time for train, train does not depart automatically after doors close");
                    } else {
                        p.sendMessage(ChatColor.RED + getLang("signimproper"));
                        e.setCancelled(true);
                    }
                    break;
                case "dir":
                    opt.setDescription("set direction for train");
                    boolean match = Arrays.asList("north", "south", "east", "west", "north_east", "north_west", "south_east", "south_west").contains(e.getLine(3).toLowerCase());
                    if (!match) {
                        p.sendMessage(ChatColor.RED + getLang("dir_notexist"));
                        e.setCancelled(true);
                    }
                    break;
                case "reg":
                    opt.setDescription("register vehicle as Untenshi vehicle");
                    break;
                default:
                    double val = parseInt(e.getLine(2));
                    opt.setDescription(val >= 0 ? "set ATO indirect pattern for train" : "set ATO direct pattern for train");
                    if (val > maxspeed) {
                        p.sendMessage(getSpeedMax());
                        e.setCancelled(true);
                    }
                    for (String i : e.getLine(3).split(" ")) {
                        parseInt(i);
                    }
                    break;
            }
            return opt.handle(p);
        } catch (Exception exception) {
            p.sendMessage(ChatColor.RED + "The number is not valid!");
            e.setCancelled(true);
        }
        return true;
    }
}