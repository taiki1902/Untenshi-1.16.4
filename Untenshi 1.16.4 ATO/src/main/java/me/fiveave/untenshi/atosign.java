package me.fiveave.untenshi;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;

class atosign extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("atosign");
    }

    @Override
    // Format: line 3: stoptime / speed
    public void execute(SignActionEvent cartevent) {
        try {
            if (cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && cartevent.hasRailedMember() && cartevent.isPowered()) {
                for (@SuppressWarnings("rawtypes") MinecartMember cart : cartevent.getMembers()) {
                    // For each passenger on cart
                    //noinspection rawtypes
                    CommonEntity cart2 = cart.getEntity();
                    //noinspection rawtypes
                    List cartpassengers = cart2.getPassengers();
                    for (Object cartobj : cartpassengers) {
                        Player p = (Player) cartobj;
                        cmds.absentDriver(p);
                        untenshi localdriver = driver.get(p);
                        if (localdriver.isPlaying() && localdriver.isAllowatousage()) {
                            if (cartevent.getLine(2).equals("stoptime")) {
                                localdriver.setAtostoptime(parseInt(cartevent.getLine(3)));
                                generalMsg(p, ChatColor.GOLD, getlang("atodetectstoptime"));
                            } else {
                                int[] loc = new int[3];
                                String[] sloc = cartevent.getLine(3).split(" ");
                                for (int a = 0; a <= 2; a++) {
                                    loc[a] = Integer.parseInt(sloc[a]);
                                }
                                localdriver.setOverrun(false);
                                double val = Double.parseDouble(cartevent.getLine(2));
                                // Direct or indirect pattern?
                                localdriver.setAtopisdirect(val < 0);
                                localdriver.setAtospeed(Math.abs(val));
                                localdriver.setAtodest(loc);
                                generalMsg(p, ChatColor.GOLD, getlang("atodetectpattern"));
                            }
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
        if (noPerm(e)) return true;
        Player p = e.getPlayer();
        try {
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "ATO sign");
            if (e.getLine(2).equals("stoptime")) {
                opt.setDescription("set ATO station stopping time for train");
                if (parseInt(e.getLine(3)) < 1) {
                    p.sendMessage(ChatColor.RED + getlang("speedpositive"));
                    e.setCancelled(true);
                }
            } else {
                double val = parseInt(e.getLine(2));
                opt.setDescription(val >= 0 ? "set ATO indirect pattern for train" : "set ATO direct pattern for train");
                if (val > maxspeed) {
                    p.sendMessage(getSpeedMax());
                    e.setCancelled(true);
                }
                for (String i : e.getLine(3).split(" ")) {
                    parseInt(i);
                }
            }
            return opt.handle(p);
        } catch (Exception exception) {
            p.sendMessage(ChatColor.RED + "The number is not valid!");
            exception.printStackTrace();
            e.setCancelled(true);
        }
        return true;
    }
}