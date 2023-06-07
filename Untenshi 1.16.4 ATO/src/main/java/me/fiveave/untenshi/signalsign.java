package me.fiveave.untenshi;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.cmds.absentDriver;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.speedsign.*;

class signalsign extends SignAction {

    static void updateSignals(Sign sign, String str) {
        sign.setLine(2, str);
        sign.update();
    }

    static void resetSignals(World world, Location[] locs) {
        try {
            // Get resetable signs
            for (Location loc : locs) {
                BlockState bs = world.getBlockAt(loc).getState();
                if (bs instanceof Sign) {
                    Sign resettable = (Sign) world.getBlockAt(loc).getState();
                    // Copy signal and speed from line 4 to line 3
                    updateSignals(resettable, "set " + resettable.getLine(3).split(" ")[1] + " " + resettable.getLine(3).split(" ")[2]);
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void signImproper(SignActionEvent cartevent, Player p) {
        String s = utshead + ChatColor.RED + getlang("signimproper") + " (" + cartevent.getLocation().getBlockX() + " " + cartevent.getLocation().getBlockY() + " " + cartevent.getLocation().getBlockZ() + ")";
        p.sendMessage(s);
        System.out.println(s);
    }

    static String signalName(String warnsi) {
        String retsi = "";
        switch (warnsi) {
            case "g":
                retsi = ChatColor.GREEN + getlang("signal" + warnsi);
                break;
            case "yg":
            case "y":
                retsi = ChatColor.YELLOW + getlang("signal" + warnsi);
                break;
            case "yy":
            case "r":
                retsi = ChatColor.RED + getlang("signal" + warnsi);
                break;
            case "atc":
                retsi = ChatColor.GOLD + getlang("signal" + warnsi);
                break;
        }
        return retsi;
    }

    // Simplify
    static boolean isSignalType(String s) {
        List<String> list = Arrays.asList("g", "yg", "y", "yy", "r", "atc");
        for (String str : list) {
            if (Objects.equals(s, str)) {
                return true;
            }
        }
        return false;
    }

    private static void removeIlShift(untenshi ld, Location targetloc) {
        if (ld.getIlposlist() != null) {
            Location[] oldpos = ld.getIlposlist();
            // Interlocking list
            for (int i1 = 0; i1 < oldpos.length; i1++) {
                if (targetloc.equals(oldpos[i1])) {
                    Location[] newpos = new Location[oldpos.length - (1 + i1)];
                    System.arraycopy(oldpos, 1 + i1, newpos, 0, newpos.length);
                    ld.setIlposlist(newpos);
                    break;
                }
            }
            // Occupied list
            Location[] oldoccupied = ld.getIlposoccupied();
            for (int i2 = 0; i2 < oldoccupied.length; i2++) {
                if (targetloc.equals(oldoccupied[i2])) {
                    Location[] newoccupied = new Location[oldoccupied.length - (1 + i2)];
                    System.arraycopy(oldoccupied, 1 + i2, newoccupied, 0, newoccupied.length);
                    ld.setIlposoccupied(newoccupied);
                    break;
                }
            }
        }
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("signalsign");
    }

    @Override
    // Format: line 3: mode, signal, speed; line 4: coord (warn, sign)
    public void execute(SignActionEvent cartevent) {
        try {
            //noinspection rawtypes
            for (MinecartMember cart : cartevent.getMembers()) {
                // For each passenger on cart
                //noinspection rawtypes
                CommonEntity cart2 = cart.getEntity();
                //noinspection rawtypes
                List cartpassengers = cart2.getPassengers();
                for (Object cartobj : cartpassengers) {
                    Player p = (Player) cartobj;
                    absentDriver(p);
                    untenshi ld = driver.get(p);
                    if (ld.isPlaying() && cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && cartevent.hasRailedMember() && cartevent.isPowered()) {
                        int signalspeed = l1(cartevent).equals("set") ? parseInt(l3(cartevent)) : 0;
                        // Main content starts here
                        if ((!(l1(cartevent).equals("warn") || l1(cartevent).equals("interlock")) && l2(cartevent).equals("del")) || (signalspeed <= maxspeed && signalspeed >= 0 && Math.floorMod(signalspeed, 5) == 0 && (checkType(cartevent)))) {
                            String signalmsg;
                            // Put signal speed limit
                            switch (l1(cartevent)) {
                                // Set signal speed limit
                                case "set":
                                    // [y][x], y for vertical (number of signals passed), x for horizontal (same row needed to be set)
                                    if (ld.getResettablesisign() == null) {
                                        ld.setResettablesisign(new Location[1]);
                                    }
                                    ld.setSignalorderptn(cartevent.getLine(3).split(" ")[0]);
                                    // Prevent stepping on same signal causing ATS run
                                    if (ld.getResettablesisign()[0] == null || !ld.getResettablesisign()[0].equals(cartevent.getLocation())) {
                                        // Except red light, signal must get reset first
                                        if (signalspeed != 0) {
                                            Location currentloc = cartevent.getLocation();
                                            // Check if that location exists in any other train, then delete that record
                                            for (Player p2 : driver.keySet()) {
                                                absentDriver(p2);
                                                untenshi ld2 = driver.get(p2);
                                                if (ld2.getResettablesisign() != null && ld2.isPlaying()) {
                                                    Location[] locs = ld2.getResettablesisign();
                                                    for (int i1 = 0; i1 < locs.length; i1++) {
                                                        if (locs[i1] != null && currentloc.equals(locs[i1])) {
                                                            locs[i1] = null;
                                                        }
                                                    }
                                                    ld2.setResettablesisign(locs);
                                                }
                                            }
                                            // If location is in interlocking list, then remove location and shift list
                                            removeIlShift(ld, currentloc);
                                        }
                                        // Set values and signal name
                                        ld.setSignallimit(signalspeed);
                                        ld.setSignaltype(l2(cartevent).equals("atc") ? "atc" : "ats");
                                        signalmsg = signalName(l2(cartevent));
                                        if (signalmsg.equals("")) {
                                            signImproper(cartevent, p);
                                            break;
                                        }
                                        String temp = signalspeed >= maxspeed ? getlang("nolimit") : signalspeed + " km/h";
                                        generalMsg(p, ChatColor.YELLOW, getlang("signalset") + signalmsg + ChatColor.GRAY + " " + temp);
                                        // If red light need to wait signal change, if not then delete variable
                                        if (signalspeed != 0) {
                                            // Get signal order
                                            List<String> ptn = signalorder.dataconfig.getStringList("signal." + ld.getSignalorderptn());
                                            int ptnlen = ptn.size();
                                            int halfptnlen = ptnlen / 2;
                                            String[] ptnsisi = new String[ptnlen];
                                            int[] ptnsisp = new int[ptnlen];
                                            for (int i = 0; i < ptnlen; i++) {
                                                if (Math.floorMod(i, 2) == 0) {
                                                    ptnsisi[i / 2] = ptn.get(i);
                                                } else {
                                                    ptnsisp[(i - 1) / 2] = parseInt(ptn.get(i));
                                                }
                                            }
                                            // Array copy (move passed signals to the back)
                                            Location[] oldloc = ld.getResettablesisign();
                                            Location[] newloc = new Location[halfptnlen];
                                            for (int i1 = 0; i1 < oldloc.length; i1++) {
                                                if (i1 + 1 < newloc.length) {
                                                    newloc[i1 + 1] = oldloc[i1];
                                                }
                                            }
                                            newloc[0] = cartevent.getLocation();
                                            // Remove variables
                                            ld.setLastsisign(null);
                                            ld.setLastsisp(-1);
                                            // Reset signals if too much (oldloc.length > newloc.length)
                                            if (oldloc.length > newloc.length) {
                                                for (int i1 = newloc.length + 1; i1 < oldloc.length; i1++) {
                                                    // Get resettable signs
                                                    resetSignals(cartevent.getWorld(), oldloc);
                                                }
                                            }
                                            ld.setResettablesisign(newloc);
                                            // Set signs with new signal and speed
                                            for (int i1 = 0; i1 < halfptnlen; i1++) {
                                                // settable: Sign to be set
                                                Sign settable;
                                                try {
                                                    settable = getSignFromLoc(newloc[i1]);
                                                    if (settable != null) {
                                                        String defaultsi = settable.getLine(3).split(" ")[1];
                                                        int defaultsp = parseInt(settable.getLine(3).split(" ")[2]);
                                                        // Check if new speed to be set is larger than default, if yes choose default instead
                                                        String str = ptnsisp[i1] > defaultsp ? defaultsi + " " + defaultsp : ptnsisi[i1] + " " + ptnsisp[i1];
                                                        Bukkit.getScheduler().runTaskLater(plugin, () -> updateSignals(settable, "set " + str), 1);
                                                    }
                                                } catch (Exception ignored) {
                                                }
                                            }
                                        }
                                        // Prevent non-resettable ATS Run caused by red light but without receiving warning
                                        else if (ld.getLastsisign() == null) {
                                            ld.setLastsisign(cartevent.getLocation());
                                            ld.setLastsisp(signalspeed);
                                        }
                                    }
                                    break;
                                // Signal speed limit warn
                                case "warn":
                                    if ((!ld.isAtsbraking() && ld.getSignaltype().equals("ats")) || ld.getSignaltype().equals("atc")) {
                                        Sign warn = getSignFromLoc(getFullLoc(cartevent.getWorld(), cartevent.getLine(3)));
                                        if (warn != null && warn.getLine(1).equals("signalsign")) {
                                            // lastsisign and lastsisp are for detecting signal change
                                            ld.setLastsisign(warn.getLocation());
                                            String warnsi = warn.getLine(2).split(" ")[1];
                                            String warnsp = warn.getLine(2).split(" ")[2];
                                            ld.setLastsisp(Integer.parseInt(warnsp));
                                            signalmsg = signalName(warnsi);
                                            if (signalmsg.equals("")) {
                                                signImproper(cartevent, p);
                                                break;
                                            }
                                            String temp2 = parseInt(warnsp) >= maxspeed ? getlang("nolimit") : warnsp + " km/h";
                                            generalMsg(p, ChatColor.YELLOW, getlang("signalwarn") + signalmsg + ChatColor.GRAY + " " + temp2);
                                        } else {
                                            signImproper(cartevent, p);
                                        }
                                    }
                                    break;
                                case "interlock":
                                    Location fullloc = getFullLoc(cartevent.getWorld(), cartevent.getLine(3));
                                    String[] l2 = cartevent.getLine(2).split(" ");
                                    Chest refsign = getChestFromLoc(fullloc);
                                    if (refsign != null) {
                                        if (l2.length == 3 && l2[2].equals("del")) {
                                            removeIlShift(ld, fullloc);
                                        } else if (l2.length == 2) {
                                            for (int itemno = 0; itemno < 27; itemno++) {
                                                ItemMeta mat = null;
                                                try {
                                                    mat = Objects.requireNonNull(refsign.getBlockInventory().getItem(itemno)).getItemMeta();
                                                } catch (Exception ignored) {
                                                }
                                                if (mat instanceof BookMeta) {
                                                    BookMeta bk = (BookMeta) mat;
                                                    int pgcount = bk.getPageCount();
                                                    for (int pgno = 1; pgno <= pgcount; pgno++) {
                                                        String str = bk.getPage(pgno);
                                                        Location[] oldilpos = ld.getIlposlist();
                                                        Location[] newilpos;
                                                        Location setloc = getFullLoc(cartevent.getWorld(), str);
                                                        // Null or not? If null just put new
                                                        if (oldilpos == null) {
                                                            newilpos = new Location[1];
                                                            newilpos[0] = setloc;
                                                        }
                                                        // If not add new ones in if not duplicated
                                                        else if (!setloc.equals(oldilpos[oldilpos.length - 1])) {
                                                            int oldilposlen = oldilpos.length;
                                                            newilpos = new Location[oldilposlen + 1];
                                                            // Array copy and set new positions
                                                            System.arraycopy(oldilpos, 0, newilpos, 0, oldilposlen);
                                                            newilpos[oldilposlen] = getFullLoc(cartevent.getWorld(), str);
                                                        }
                                                        // If duplicated just copy old to new
                                                        else {
                                                            newilpos = oldilpos;
                                                        }
                                                        ld.setIlposlist(newilpos);
                                                        ld.setIlenterqueuetime(System.currentTimeMillis());
                                                    }
                                                    ld.setSignalorderptn(cartevent.getLine(2).split(" ")[1]);
                                                }
                                            }
                                        }
                                    }
                                    break;
                                default:
                                    signImproper(cartevent, p);
                                    break;
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
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "Signal sign");
            // Check signal name
            if (!checkType(e) && !l2(e).equals("del")) {
                p.sendMessage(ChatColor.RED + getlang("signaltypewrong"));
                p.sendMessage(ChatColor.RED + getlang("signalargwrong"));
                e.setCancelled(true);
            }
            // Check speed conditions
            if (l1(e).equals("set")) {
                if (parseInt(l3(e)) > maxspeed) {
                    p.sendMessage(getSpeedMax());
                    e.setCancelled(true);
                }
                if (parseInt(l3(e)) < 0) {
                    p.sendMessage(ChatColor.RED + getlang("speedmin0"));
                    e.setCancelled(true);
                }
                if (Math.floorMod(parseInt(l3(e)), 5) != 0) {
                    p.sendMessage(ChatColor.RED + getlang("speeddiv5"));
                    e.setCancelled(true);
                }

            }
            // Check line 4 (coord) is int only
            String[] s2 = e.getLine(2).split(" ");
            String[] s3 = e.getLine(3).split(" ");
            switch (l1(e)) {
                case "warn":
                    for (String i : s3) {
                        parseInt(i);
                    }
                    opt.setDescription("set signal speed warning for train");
                    break;
                case "interlock":
                    if (s2.length != 2 && !s2[2].equals("del")) {
                        e.setCancelled(true);
                    }
                    for (String i : s3) {
                        parseInt(i);
                    }
                    opt.setDescription("set interlocking path for train");
                    break;
                case "set":
                    if (!isSignalType(s3[1].toLowerCase())) {
                        p.sendMessage(ChatColor.RED + getlang("signaltypewrong"));
                    }
                    parseInt(s3[2]);
                    if (parseInt(s3[2]) > maxspeed) {
                        p.sendMessage(getSpeedMax());
                        e.setCancelled(true);
                    }
                    if (parseInt(s3[2]) < 0) {
                        p.sendMessage(ChatColor.RED + getlang("speedmin0"));
                        e.setCancelled(true);
                    }
                    if (Math.floorMod(parseInt(s3[2]), 5) != 0) {
                        p.sendMessage(ChatColor.RED + getlang("speeddiv5"));
                        e.setCancelled(true);
                    }
                    opt.setDescription("set signal speed limit for train");
                    break;
            }
            return opt.handle(p);
        } catch (Exception exception) {
            p.sendMessage(ChatColor.RED + getlang("signimproper"));
            exception.printStackTrace();
            e.setCancelled(true);
        }
        return true;
    }

    boolean checkType(SignActionEvent e) {
        return l1(e).equals("warn") || l1(e).equals("interlock") || (l1(e).equals("set") && isSignalType(l2(e)));
    }
}