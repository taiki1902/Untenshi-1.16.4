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
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.speedsign.getSign;

public class signalsign extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("signalsign");
    }

    @Override
    // Format: line 3: mode, signal, speed; line 4: coord (warn, sign)
    public void execute(SignActionEvent cartevent) {
        try {
            Player idk = null;
            //noinspection rawtypes
            for (MinecartMember cart : cartevent.getMembers()) {
                // For each passenger on cart
                //noinspection rawtypes
                CommonEntity cart2 = cart.getEntity();
                //noinspection rawtypes
                List cartpassengers = cart2.getPassengers();
                for (Object cartobj : cartpassengers) {
                    Player p = (Player) cartobj;
                    idk = p;
                    if (playing.containsKey(p)) {
                        signaltype.putIfAbsent(p, "ats");
                        if (playing.get(p) && cartevent.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && cartevent.hasRailedMember() && cartevent.isPowered()) {
                            int signalspeed = 0;
                            if (l1(cartevent).equals("set") || l1(cartevent).equals("sign")) {
                                signalspeed = parseInt(l3(cartevent));
                            }
                            // Main content starts here
                            if (signalspeed <= 360 && signalspeed >= 0 && Math.floorMod(signalspeed, 5) == 0 && (checktype(cartevent))) {
                                String signalmsg = "";
                                // Put signal speed limit
                                switch (l1(cartevent)) {
                                    // Set signal speed limit
                                    case "set":
                                        lastresetablesign.putIfAbsent(p, new Location[1]);
                                        // Update sign with line 4
                                        if (cartevent.getLine(3).isEmpty()) {
                                            String str = "default";
                                            if (signalorderptn.containsKey(p)) {
                                                str = signalorderptn.get(p);
                                            }
                                            cartevent.setLine(3, str + " " + l2(cartevent) + " " + signalspeed);
                                        } else {
                                            signalorderptn.put(p, cartevent.getLine(3).split(" ")[0]);
                                        }
                                        // Check if that location exists in any other train, then delete that record
                                        Location currentloc = cartevent.getLocation();
                                        for (Player p2 : plugin.getServer().getOnlinePlayers()) {
                                            playing.putIfAbsent(p2, false);
                                            if (playing.get(p2)) {
                                                lastresetablesign.putIfAbsent(p2, new Location[1]);
                                                Location[] locs = lastresetablesign.get(p2);
                                                for (int i = 0; i < locs.length; i++) {
                                                    if (locs[i] == currentloc) {
                                                        locs[i] = null;
                                                        lastresetablesign.put(p, locs);
                                                        resetSignals(cartevent.getWorld(), locs[i]);
                                                    }
                                                }
                                            }
                                        }
                                        signallimit.put(p, signalspeed);
                                        if (!l2(cartevent).equals("atc")) {
                                            signaltype.put(p, "ats");
                                        } else {
                                            signaltype.put(p, "atc");
                                        }
                                        signalmsg = signalName(l2(cartevent), signalmsg);
                                        if (signalmsg.equals("")) {
                                            signimproper(cartevent, p);
                                            break;
                                        }
                                        String temp = signalspeed + " km/h";
                                        if (signalspeed >= 360) {
                                            temp = getlang("nolimit");
                                        }
                                        p.sendMessage(utshead + ChatColor.YELLOW + getlang("signalset") + signalmsg + ChatColor.GRAY + " " + temp);
                                        // If red light need to wait signal change, if not then delete variable
                                        if (signalspeed != 0) {
                                            // Get signal order
                                            List<String> ptn = signalorder.dataconfig.getStringList("signal." + signalorderptn.get(p));
                                            int ptnlen = ptn.size();
                                            int halfptnlen = ptnlen / 2;
                                            String[] ptnsisi = new String[ptnlen];
                                            int[] ptnsisp = new int[ptnlen];
                                            for (int i = 0; i < ptnlen; i++) {
                                                if (Math.floorMod(i, 2) == 0) {
                                                    ptnsisi[i / 2] = ptn.get(i);
                                                } else {
                                                    ptnsisp[(i - 1) / 2] = Integer.parseInt(ptn.get(i));
                                                }
                                            }
                                            Location[] newloc = new Location[halfptnlen];
                                            Location[] oldloc = lastresetablesign.get(p);
                                            for (int i = 0; i < oldloc.length; i++) {
                                                if (i + 1 < newloc.length) {
                                                    newloc[i + 1] = oldloc[i];
                                                }
                                            }
                                            newloc[0] = cartevent.getLocation();
                                            // Remove variables
                                            lastsisign.remove(p);
                                            lastsisp.remove(p);
                                            // Reset signals if too much (oldloc.length > newloc.length)
                                            if (oldloc.length > newloc.length) {
                                                for (int i = newloc.length + 1; i < oldloc.length; i++) {
                                                    // Get resetable signs
                                                    resetSignals(cartevent.getWorld(), oldloc[i]);
                                                }
                                            }
                                            // Set signs with new signal and speed
                                            for (int i = 0; i < halfptnlen; i++) {
                                                Sign setable = null;
                                                try {
                                                    setable = (Sign) cartevent.getWorld().getBlockAt(newloc[i]).getState();
                                                } catch (Exception ignored) {
                                                }
                                                if (setable != null) {
                                                    int defaultsp = Integer.parseInt(setable.getLine(3).split(" ")[2]);
                                                    String str = ptnsisi[i] + " " + ptnsisp[i];
                                                    if (ptnsisp[i] > defaultsp) {
                                                        str = ptnsisi[i] + " " + defaultsp;
                                                    }
                                                    Sign finalSetable = setable;
                                                    String finalStr = str;
                                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                        finalSetable.setLine(2, "set " + finalStr);
                                                        finalSetable.update();
                                                    }, 1);
                                                }
                                            }
                                            lastresetablesign.put(p, newloc);
                                        }
                                        break;
                                    // Signal speed limit warn
                                    case "warn":
                                        if ((!atsebing.get(p) && signaltype.get(p).equals("ats")) || signaltype.get(p).equals("atc")) {
                                            Sign warn = getSign(cartevent);
                                            if (warn != null && warn.getLine(1).equals("signalsign")) {
                                                // lastsisign and lastsisp are for detecting signal change
                                                lastsisign.put(p, warn.getLocation());
                                                String warnsi = warn.getLine(2).split(" ")[1];
                                                String warnsp = warn.getLine(2).split(" ")[2];
                                                lastsisp.put(p, Integer.valueOf(warnsp));
                                                signalmsg = signalName(warnsi, signalmsg);
                                                if (signalmsg.equals("")) {
                                                    signimproper(cartevent, p);
                                                    break;
                                                }
                                                String temp2 = warnsp + " km/h";
                                                if (parseInt(warnsp) >= 360) {
                                                    temp2 = getlang("nolimit");
                                                }
                                                p.sendMessage(utshead + ChatColor.YELLOW + getlang("signalwarn") + signalmsg + ChatColor.GRAY + " " + temp2);
                                            } else {
                                                signimproper(cartevent, p);
                                            }
                                        }
                                        break;
                                    case "sign":
                                        if (!overrun.get(p)) {
                                            // Set line 4 of sign at (line 3 of this sign) to turn signal
                                            changesign(cartevent);
                                        }
                                        break;
                                    default:
                                        signimproper(cartevent, p);
                                        break;
                                }
                            }
                        }
                    }
                }
            }
            if (cartevent.isAction(SignActionType.REDSTONE_ON) && cartevent.isPowered()) {
                // Set line 4 of sign at (line 3 of this sign) to turn signal
                if (l1(cartevent).equals("sign")) {
                    if (!overrun.containsKey(idk) || !overrun.get(idk)) {
                        changesign(cartevent);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void resetSignals(World world, Location locs) {
        try {
            // Get resetable signs
            Sign resetable = (Sign) world.getBlockAt(locs).getState();
            // Copy signal and speed from line 4 to line 3
            resetable.setLine(2, "set " + resetable.getLine(3).split(" ")[1] + " " + resetable.getLine(3).split(" ")[2]);
            resetable.update();
        } catch (Exception ignored) {
        }
    }

    protected static void signimproper(SignActionEvent cartevent, Player p) {
        String s = utshead + ChatColor.RED + getlang("signimproper") + " (" + cartevent.getLocation().getX() + " " + cartevent.getLocation().getY() + " " + cartevent.getLocation().getZ() + ")";
        p.sendMessage(s);
        System.out.println(s);
    }

    private void changesign(SignActionEvent cartevent) {
        Sign sign = getSign(cartevent);
        if (sign != null && sign.getLine(2).split(" ")[0].equals("set")) {
            sign.setLine(2, "set " + l2(cartevent) + " " + l3(cartevent));
            sign.update();
        }
    }

    protected static String signalName(String warnsi, String sigmsg) {
        if (warnsi.equals("gy")) {
            warnsi = "yg";
        }
        switch (warnsi) {
            case "g":
                sigmsg = ChatColor.GREEN + getlang("signal" + warnsi);
                break;
            case "yg":
            case "y":
                sigmsg = ChatColor.YELLOW + getlang("signal" + warnsi);
                break;
            case "yy":
            case "r":
                sigmsg = ChatColor.RED + getlang("signal" + warnsi);
                break;
            case "atc":
                sigmsg = ChatColor.GOLD + getlang("signal" + warnsi);
                break;
        }
        return sigmsg;
    }

    @Override
    public boolean build(SignChangeActionEvent e) {
        if (noperm(e)) return true;
        try {
            SignBuildOptions opt = SignBuildOptions.create().setName(ChatColor.GOLD + "Signal sign");
            // Check signal name
            if (checktype(e)) {
                opt.setDescription("set signal speed limit for train");
            } else {
                e.getPlayer().sendMessage(ChatColor.RED + getlang("signalarg32"));
                e.setCancelled(true);
            }
            // Check if speed can mod 5
            if (l1(e).equals("set") || l1(e).equals("sign")) {
                if (parseInt(l3(e)) > 360) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signalmax360"));
                    e.setCancelled(true);
                }
                if (parseInt(l3(e)) < 0) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signalmin0"));
                    e.setCancelled(true);
                }
                if (Math.floorMod(parseInt(l3(e)), 5) != 0) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signaldiv5"));
                    e.setCancelled(true);
                }

            }

            // Check line 4 (coord) is int only
            if (!l1(e).equals("set")) {
                for (String i : e.getLine(3).split(" ")) {
                    parseInt(i);
                }
            } else {
                String[] line4 = e.getLine(3).split(" ");
                if (!issignaltype(line4[1].toLowerCase())) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signalarg42"));
                }
                parseInt(line4[2]);
                if (parseInt(line4[2]) > 360) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signalmax360"));
                    e.setCancelled(true);
                }
                if (parseInt(line4[2]) < 0) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signalmin0"));
                    e.setCancelled(true);
                }
                if (Math.floorMod(parseInt(line4[2]), 5) != 0) {
                    e.getPlayer().sendMessage(ChatColor.RED + getlang("signaldiv5"));
                    e.setCancelled(true);
                }
            }
            return opt.handle(e.getPlayer());
        } catch (Exception exception) {
            e.getPlayer().sendMessage(ChatColor.RED + getlang("signimproper"));
            exception.printStackTrace();
            e.setCancelled(true);
        }
        return true;
    }

    // Simplify
    protected static boolean issignaltype(String s) {
        List<String> list = Arrays.asList("g", "yg", "gy", "y", "yy", "r", "atc");
        for (String str : list) {
            if (Objects.equals(s, str)) {
                return true;
            }
        }
        return false;
    }

    protected boolean checktype(SignActionEvent e) {
        return l1(e).equals("warn") || ((l1(e).equals("set") || l1(e).equals("sign")) && issignaltype(l2(e)));
    }

    // l[n]: "n"th split text in line 3
    protected String l1(SignActionEvent e) {
        return e.getLine(2).toLowerCase().split(" ")[0];
    }

    protected static String l2(SignActionEvent e) {
        return e.getLine(2).toLowerCase().split(" ")[1];
    }

    protected String l3(SignActionEvent e) {
        return e.getLine(2).split(" ")[2];
    }
}