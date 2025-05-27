package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.ChatColor;

import static me.fiveave.untenshi.main.noSignPerm;
import static me.fiveave.untenshi.main.vehicle;

class utstrain extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("utstrain");
    }

    @Override
    public void execute(SignActionEvent cartevent) {
        if (cartevent.hasRailedMember() && cartevent.isPowered()) {
            if (vehicle.get(cartevent.getGroup()) != null) {
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
        if (noSignPerm(e)) return true;
        try {
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "Untenshi Train Detector");
            opt.setDescription("detect if the train is a Untenshi train");
            return opt.handle(e.getPlayer());
        } catch (Exception exception) {
            e.getPlayer().sendMessage(ChatColor.RED + "Numbers are not valid!");
            e.setCancelled(true);
        }
        return true;
    }
}