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

import static me.fiveave.untenshi.main.playing;

public class utstrain extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("utstrain");
    }

    @Override
    public void execute(SignActionEvent cartevent) {
        if (cartevent.hasRailedMember() && cartevent.isPowered()) {
            int utsplayers = 0;
            //noinspection rawtypes
            for (MinecartMember cart : cartevent.getMembers()) {
                // For each passenger on cart
                //noinspection rawtypes
                CommonEntity cart2 = cart.getEntity();
                //noinspection rawtypes
                List cartps = cart2.getPassengers();
                for (Object cartobject : cartps) {
                    Player p = (Player) cartobject;
                    playing.putIfAbsent(p, false);
                    if (playing.get(p)) {
                        utsplayers++;
                    }
                }
            }
            if (utsplayers > 0) {
                cartevent.setLevers(true);
            }
            if (cartevent.isAction(SignActionType.GROUP_LEAVE)) {
                cartevent.setLevers(false);
            }
        } else {
            cartevent.setLevers(false);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent e) {
        if (e.getPlayer().hasPermission("uts.sign")) {
            try {
                SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "UTS Train Detector");
                opt.setDescription("detect if the train is a UTS train");
                return opt.handle(e.getPlayer());
            } catch (Exception exception) {
                e.getPlayer().sendMessage(ChatColor.RED + "Numbers are not valid!");
                e.setCancelled(true);
            }
        } else {
            e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission!");
            e.setCancelled(true);
        }
        return true;
    }
}