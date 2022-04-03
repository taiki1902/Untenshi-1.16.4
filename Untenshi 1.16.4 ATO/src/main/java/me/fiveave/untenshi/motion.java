package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.Set;

import static me.fiveave.untenshi.ato.atosys;
import static me.fiveave.untenshi.events.doorControls;
import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.main.stoppos;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signalName;

public class motion {

    public static void recursion1(CommandSender sender) {
        Player ctrlp = (Player) sender;
        if (playing.containsKey(ctrlp) && playing.get(ctrlp) && ctrlp.isInsideVehicle()) {
            recursion2(ctrlp);
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursion1(sender), 2);
        } else if (!ctrlp.isInsideVehicle() && !frozen.get(ctrlp)) {
            Bukkit.dispatchCommand(ctrlp, "uts activate false");
        } else if (frozen.get(ctrlp)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursion1(sender), 2);
        } else {
            mascon.put(ctrlp, -9);
            speed.put(ctrlp, 0.0);
        }
    }

    protected static Integer getmascon(Player ctrlplayer) {
        return mascon.get(ctrlplayer);
    }

    public static void recursion2(Player p) {
        // From Config
        double accel;
        double decel;
        double ebdecel;
        int[] speedsteps;
        switch (traintype.get(p)) {
            default:
            case "local":
                ebdecel = 10.4;
                speedsteps = new int[]{21, 41, 61, 81, 111, 130};
                accel = plugin.getConfig().getDouble(traintype.get(p) + "accelrate");
                decel = plugin.getConfig().getDouble(traintype.get(p) + "decelrate");
                break;
            case "hsr":
                ebdecel = 10.4;
                speedsteps = new int[]{41, 101, 161, 221, 261, 280};
                accel = plugin.getConfig().getDouble(traintype.get(p) + "accelrate");
                decel = plugin.getConfig().getDouble(traintype.get(p) + "decelrate");
                break;
            case "lrt":
                ebdecel = 19;
                speedsteps = new int[]{16, 31, 46, 61, 71, 80};
                accel = plugin.getConfig().getDouble("local" + "accelrate") * 4.68 / 3.5;
                decel = plugin.getConfig().getDouble("local" + "decelrate") * 3.6 / 3.5;
                break;
        }
        double oldspeed = speed.get(p);
        double speeddrop = plugin.getConfig().getDouble("speeddroprate") / 10;
        boolean stationstop = plugin.getConfig().getBoolean("stationsignstop");
        // Init train
        MinecartGroup mg = MinecartGroupStore.get(p.getVehicle());
        TrainProperties tprop = mg.getProperties();
        // From traindata (if available)
        Set<String> allTrains = Objects.requireNonNull(traindata.dataconfig.getConfigurationSection("trains")).getKeys(false);
        for (Object tname : allTrains) {
            String tname2 = tname.toString();
            // Override config accels
            if (mg.getProperties().getDisplayName().contains(tname2)) {
                String tDataInfo = "trains." + tname2;
                if (tcontains(".accel", tDataInfo))
                    accel = traindata.dataconfig.getDouble(tDataInfo + ".accel");
                if (tcontains(".decel", tDataInfo))
                    decel = traindata.dataconfig.getDouble(tDataInfo + ".decel");
                if (tcontains(".traintype", tDataInfo))
                    traintype.put(p, traindata.dataconfig.getString(tDataInfo + ".traintype"));
                if (tcontains(".ebdecel", tDataInfo))
                    ebdecel = traindata.dataconfig.getDouble(tDataInfo + ".ebdecel") * 2;
                if (tcontains(".speeds", tDataInfo) && traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").size() == 6) {
                    try {
                        for (int i = 0; i < 6; i++) {
                            speedsteps[i] = traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").get(i);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // Initialize
        accel /= 10;
        decel /= 10;
        lasty.putIfAbsent(p, Objects.requireNonNull(p.getVehicle()).getLocation().getY());
        // Electric current brake
        current.putIfAbsent(p, -480.0);
        double ecb = 0;
        double currentnow = current.get(p);
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df2 = new DecimalFormat("#.##");
        DecimalFormat df0 = new DecimalFormat("#");
        DecimalFormat df0up = new DecimalFormat("#");
        df0up.setRoundingMode(RoundingMode.UP);
        // Set current for current mascon
        if (getmascon(p) >= -9 && getmascon(p) <= 5) {
            ecb = Integer.parseInt(df0up.format(480.0 / 9 * getmascon(p)));
        }
        // Set real current
        if (ecb < currentnow) {
            if ((currentnow - ecb) > 80.0 / 3) {
                current.put(p, (currentnow - 80 / 3));
            } else {
                current.put(p, ecb);
            }
        } else if (ecb > currentnow) {
            if ((ecb - currentnow) > 80.0 / 3) {
                current.put(p, (currentnow + 80 / 3));
            } else {
                current.put(p, ecb);
            }
        }
        double dcurrent = currentnow * 9 / 480;
        // Accel and decel
        // i1-4: Ref decelswitch
        int i1 = speedsteps[0];
        int i2 = speedsteps[1];
        int i3 = speedsteps[2];
        int i4 = speedsteps[3];
        double stopdecel = decelswitch(p, speeddrop, decel, dcurrent, speed.get(p), i1, i2, i3, i4, ebdecel);
        if (dooropen.get(p) == 0) {
            accelswitch(p, accel, (int) dcurrent, speed.get(p), speedsteps);
            speed.put(p, speed.get(p) - stopdecel);
        }
        // Have speed drop even if no accel
        speed.put(p, (speed.get(p) - speeddrop * 2));
        // ATS Forced Controls
        if (mascon.get(p).equals(-9)) {
            atsforced.putIfAbsent(p, 0);
            if (atsforced.get(p) >= 10) {
                speed.put(p, speed.get(p) - 3.5);
            }
        } else {
            atsforced.put(p, 0);
        }
        // Ctrl Text
        String ctrltext = "";
        if (getmascon(p) == -9) {
            ctrltext = ChatColor.DARK_RED + "EB";
        } else if (getmascon(p) >= -8 && getmascon(p) <= -1) {
            ctrltext = ChatColor.RED + "B" + Math.abs(getmascon(p));
        } else if (getmascon(p) == 0) {
            ctrltext = ChatColor.WHITE + "N";
        } else if (getmascon(p) >= 1 && getmascon(p) <= 5) {
            ctrltext = ChatColor.GREEN + "P" + getmascon(p);
        }
        // Set speed to 0 when under 0
        if (speed.get(p) < 0) {
            speed.put(p, 0.0);
        }
        //// Slope speed adjust
        double trainy = p.getVehicle().getLocation().getY();
        speed.put(p, Double.parseDouble(df3.format(speed.get(p) + (lasty.get(p) - trainy))));
        lasty.put(p, trainy);
        df3.setRoundingMode(RoundingMode.CEILING);
        speed.put(p, Double.valueOf(df3.format(speed.get(p))));
        // Set speed (Anti VictorXcraft beikeiyaroe)
        if (!stationstop) mg.getActions().clear();
        /* ATO (Automatic train operation)
         * Note: This system is experimental, do not use it until it is finished
         */
        atosys(p, decel, ebdecel, speeddrop, i1, i2, i3, i4);
        // Shock when stopping
        String shock = "";
        if (speed.get(p) == 0 && speed.get(p) < oldspeed) {
            shock = " " + ChatColor.GRAY + df2.format(stopdecel * 10) + " km/h/s";
        }
        // Combine properties and action bar
        tprop.setSpeedLimit(Double.parseDouble(df3.format(speed.get(p) / 72)));
        mg.setForwardForce(Double.parseDouble(df3.format(speed.get(p) / 72)));
        mg.setProperties(tprop);
        String actionbarmsg = "" + ctrltext + ChatColor.WHITE + " | " + ChatColor.YELLOW + getlang("speed") + ChatColor.WHITE + df0.format(speed.get(p)) + " km/h" + " | " + ChatColor.YELLOW + getlang("points") + ChatColor.WHITE + points.get(p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Count points
        if (!freemode.get(p) && getmascon(p).equals(-9) && speed.get(p) > 20 && !atsing.get(p) && !atsebing.get(p)) {
            // Misuse EB
            if (deductdelay.get(p) >= 50) {
                deductdelay.put(p, 0);
                pointCounter(p, getlang("misuseeb"), -5, "");
            } else {
                deductdelay.put(p, deductdelay.get(p) + 1);
            }
        }
        // Get signal update when warn (if signal speed isn't same)
        if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
            Sign warn = (Sign) p.getWorld().getBlockAt(lastsisign.get(p)).getState();
            String warnsi = warn.getLine(2).split(" ")[1];
            int warnsp = Integer.parseInt(warn.getLine(2).split(" ")[2]);
            // Detect difference (saved sign speed != sign speed now)
            if (warnsp != lastsisp.get(p)) {
                lastsisp.put(p, warnsp);
                String signalmsg = "";
                signalmsg = signalName(warnsi, signalmsg);
                // If red light
                if (atsebing.get(p) && signallimit.get(p) == 0) {
                    // Remove lastsisign and lastsisp as need to detect further signal warnings
                    signallimit.put(p, warnsp);
                    lastsisign.remove(p);
                    lastsisp.remove(p);
                }
                if (warnsp >= 360) {
                    p.sendMessage(utshead + ChatColor.YELLOW + getlang("signalchange") + signalmsg + ChatColor.GRAY + " " + getlang("nolimit"));
                } else {
                    p.sendMessage(utshead + ChatColor.YELLOW + getlang("signalchange") + signalmsg + ChatColor.GRAY + " " + warnsp + " km/h");
                }
            }
        }
        // ATS-P
        double signaldist;
        double speeddist;
        double lowerSpeed;
        double shortestDist;
        boolean isoverspeed0 = speed.get(p) > signallimit.get(p) || speed.get(p) > speedlimit.get(p);
        boolean isoverspeed3 = speed.get(p) > signallimit.get(p) + 3 || speed.get(p) > speedlimit.get(p) + 3;
        if (signaltype.get(p).equals("ats")) {
            try {
                speeddist = Math.sqrt(Math.pow(lastspsign.get(p).getX() + 0.5 - p.getLocation().getX(), 2) + Math.pow(lastspsign.get(p).getZ() + 0.5 - p.getLocation().getZ(), 2));
            } catch (Exception e) {
                speeddist = Double.MAX_VALUE;
            }
            try {
                signaldist = Math.sqrt(Math.pow(lastsisign.get(p).getX() + 0.5 - p.getLocation().getX(), 2) + Math.pow(lastsisign.get(p).getZ() + 0.5 - p.getLocation().getZ(), 2));
            } catch (Exception e) {
                signaldist = Double.MAX_VALUE;
            }
            shortestDist = Math.min(signaldist, speeddist);
            if (shortestDist == signaldist) {
                try {
                    lowerSpeed = Math.min(Math.min(signallimit.get(p), lastsisp.get(p)), speedlimit.get(p));
                } catch (Exception e) {
                    lowerSpeed = Math.min(signallimit.get(p), speedlimit.get(p));
                }
            } else {
                try {
                    lowerSpeed = Math.min(Math.min(speedlimit.get(p), lastspsp.get(p)), signallimit.get(p));
                } catch (Exception e) {
                    lowerSpeed = Math.min(speedlimit.get(p), signallimit.get(p));
                }
            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            reqdist[9] = getreqdist(p, 10 * gendefm(decel, speed.get(p), ebdecel, i1, i2, i3, i4), lowerSpeed);
            for (int a = 5; a <= 8; a++) {
                reqdist[a] = getreqdist(p, 10 * gendefm(decel, speed.get(p), a + 1, i1, i2, i3, i4), lowerSpeed);
            }
            // Pattern run
            if (((shortestDist < reqdist[8] && speed.get(p) > lowerSpeed + 3) || isoverspeed3) && !atsping.get(p)) {
                if (!atsping.get(p) && !atsebing.get(p)) {
                    deductspeeding(p);
                }
                atsping.put(p, true);
                if ((lowerSpeed == 0 && shortestDist - reqdist[5] < 1 && shortestDist < 5) || signallimit.get(p).equals(0)) {
                    mascon.put(p, -9);
                    p.sendMessage(utshead + ChatColor.RED + getlang("atspeb"));
                } else {
                    mascon.put(p, -8);
                    p.sendMessage(utshead + ChatColor.RED + getlang("atspb8"));
                }
            } else if (shortestDist > reqdist[8] && !isoverspeed0) {
                atsping.put(p, false);
            }
            // Pattern near
            if (!atspnear.get(p) && (shortestDist < reqdist[5] || isoverspeed0)) {
                atspnear.put(p, true);
                p.sendMessage(utshead + ChatColor.GOLD + getlang("atspnear"));
            } else if (shortestDist > reqdist[5] && !isoverspeed0) {
                atspnear.put(p, false);
            }
        }
        // ATC Signal Speeding (not atsebing, speed limit is 0 or other +3 km/h), Speed limit speeding (+3 km/h)
        if (!atsebing.get(p) && isoverspeed3 && signaltype.get(p).equals("atc")) {
            if (!atsping.get(p) && !atsebing.get(p)) {
                deductspeeding(p);
            }
            if (!atsping.get(p)) {
                p.sendMessage(utshead + ChatColor.RED + getlang("atcrun"));
            }
        }
        // Instant ATS / ATC if red light
        if (signallimit.get(p).equals(0)) {
            atsebing.put(p, true);
            atsing.put(p, false);
            atsdelay.put(p, 0);
            mascon.put(p, -9);
            current.put(p, -480.0);
        }
        // ATC Auto Control
        if (signaltype.get(p).equals("atc")) {
            if (signallimit.get(p).equals(0)) {
                atsebing.put(p, true);
                mascon.put(p, -9);
                current.put(p, -480.0);
            } else if (signallimit.get(p) > 0) {
                if (!atsebing.get(p) && !atsing.get(p) && isoverspeed3) {
                    atsebing.put(p, true);
                    mascon.put(p, -8);
                } else if (!isoverspeed3) {
                    atsebing.put(p, false);
                }
            }
        }
        // Door (enter and exit train)
        if (doordiropen.get(p) && dooropen.get(p) < 30) {
            dooropen.put(p, dooropen.get(p) + 1);
        } else if (!doordiropen.get(p) && dooropen.get(p) > 0) {
            dooropen.put(p, dooropen.get(p) - 1);
        }
        if (dooropen.get(p).equals(0) && !doorconfirm.get(p)) {
            tprop.setPlayersEnter(false);
            tprop.setPlayersExit(false);
            doorconfirm.put(p, true);
            p.sendMessage(utshead + ChatColor.YELLOW + getlang("door") + ChatColor.RED + getlang("closed"));
        } else if (dooropen.get(p).equals(30) && !doorconfirm.get(p)) {
            tprop.setPlayersEnter(true);
            tprop.setPlayersExit(true);
            doorconfirm.put(p, true);
            p.sendMessage(utshead + ChatColor.YELLOW + getlang("door") + ChatColor.GREEN + getlang("opened"));
        }
        // Stop position
        if (reqstopping.get(p)) {
            // Get stop location
            double locx = p.getLocation().getX();
            double locz = p.getLocation().getZ();
            double stopdist = Double.parseDouble(df2.format(Math.sqrt(Math.pow(locx - stoppos.get(p)[0], 2) + Math.pow(locz - stoppos.get(p)[2], 2))));
            int stopdistcm = (int) (stopdist * 100);
            // Start Overrun
            if (stopdist < 2 && !overrun.get(p)) {
                overrun.put(p, true);
            }
            // Rewards and penalties
            // In station EB
            if (getmascon(p) == -9 && !staeb.get(p) && !plugin.getConfig().getBoolean("allowebstop")) {
                staeb.put(p, true);
            }
            // In station accel
            if (getmascon(p) >= 1 && !staaccel.get(p) && !plugin.getConfig().getBoolean("allowreaccel")) {
                staaccel.put(p, true);
            }
            // Stop positions
            // <= 2 m
            if (stopdist <= 2.00 && speed.get(p) == 0) {
                //// 25 cm
                if (stopdist <= 0.25) {
                    // Need to fix stop pos? If no then add points
                    showstoppos(p, "stopposperfect", stopdistcm, shock, 10);
                    //// 50 cm
                } else if (stopdist <= 0.50) {
                    showstoppos(p, "stopposgreat", stopdistcm, shock, 5);
                    //// 1 m
                } else if (stopdist <= 1.00) {
                    showstoppos(p, "stopposgood", stopdistcm, shock, 3);
                }
                opendoorprocedure(p);
                // < 50 m
            } else if (stopdist < 50 && speed.get(p) == 0 && overrun.get(p) && !fixstoppos.get(p)) {
                fixstoppos.put(p, true);
                staaccel.put(p, false);
                if (!freemode.get(p)) {
                    pointCounter(p, getlang("stopposover"), Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    p.sendMessage(utshead + ChatColor.YELLOW + getlang("stopposover") + ChatColor.RED + Math.round(stopdist) + "m" + shock);
                }
                //// cho-hetadane!
            } else if (stopdist >= 50 && overrun.get(p)) {
                if (!freemode.get(p)) {
                    if (speed.get(p) > 0) {
                        atsforced.put(p, 10);
                        mascon.put(p, -9);
                    } else {
                        p.sendMessage(utshead + ChatColor.RED + getlang("stopposseriousover"));
                        Bukkit.dispatchCommand(p, "uts activate false");
                    }
                } else {
                    reqstopping.put(p, false);
                    overrun.put(p, false);
                }
            }
        }
        // Catch point <= 0 and end game
        if (!freemode.get(p) && points.get(p) <= 0) {
            p.sendMessage(utshead + ChatColor.RED + getlang("nopoints"));
            Bukkit.dispatchCommand(p, "uts activate false");
        }
    }

    private static void showstoppos(Player p, String stopposeval, int stopdistcm, String shock, int pts) {
        if (!fixstoppos.get(p)) {
            String s = " ";
            if (!freemode.get(p)) {
                s = " " + ChatColor.GREEN + "+" + pts + " ";
                points.put(p, (points.get(p) + pts));
            }
            p.sendMessage(utshead + ChatColor.YELLOW + getlang(stopposeval) + s + ChatColor.GRAY + stopdistcm + "cm" + shock);
        }
    }

    private static void deductspeeding(Player p) {
        if (!freemode.get(p)) {
            if (speed.get(p) > speedlimit.get(p) + 3) {
                pointCounter(p, getlang("speeding"), -5, "");
            }
            if (speed.get(p) > signallimit.get(p) + 3) {
                pointCounter(p, getlang("signalspeeding"), -5, "");
            }
        }
    }


    // Reset values, open doors, reset ATO
    private static void opendoorprocedure(Player ctrlp) {
        reqstopping.put(ctrlp, false);
        fixstoppos.put(ctrlp, false);
        doorControls(ctrlp, true);
        if (atospeed.containsKey(ctrlp)) {
            mascon.put(ctrlp, -8);
        }
        atodest.remove(ctrlp);
        atospeed.remove(ctrlp);
    }

    protected static double getreqdist(Player ctrlp, double decel, double lowerSpeed) {
        return (Math.pow(speed.get(ctrlp), 2) / (7.2 * decel)) - (Math.pow(lowerSpeed, 2) / (7.2 * decel));
    }

    protected static double decelswitch(Player ctrlp, double speeddrop, double decel, double dcurrent, double cspd, int i1, int i2, int i3, int i4, double ebrate) {
        double decelvalue = 0;
        if (dcurrent == 0) {
            if (speed.containsKey(ctrlp)) {
                decelvalue = speeddrop;
            }
        } else if (dcurrent < 0 && dcurrent > -9) {
            decelvalue = gendefm(decel, cspd, Math.abs(dcurrent) + 1, i1, i2, i3, i4);
        } else if (dcurrent == -9) {
            if (!atsebing.get(ctrlp) && !signallimit.get(ctrlp).equals(0)) {
                decelvalue = gendefm(decel, cspd, ebrate, i1, i2, i3, i4);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                atsforced.put(ctrlp, 10);
            }
        }
        return decelvalue;
    }

    protected static void accelswitch(Player ctrlp, double accel, double dcurrent, double cspd, int[] sec) {
        if (dcurrent > 0) {
            if (cspd < sec[0]) {
                accelpower(ctrlp, accel, cspd, 7);
            } else if (cspd < sec[1]) {
                accelpower(ctrlp, accel, cspd, dcurrent > 1 ? 6 : 3);
            } else if (cspd < sec[2]) {
                accelpower(ctrlp, accel, cspd, dcurrent > 2 ? 5 : dcurrent > 1 ? 3 : 0);
            } else if (cspd < sec[3]) {
                accelpower(ctrlp, accel, cspd, dcurrent > 3 ? 4 : dcurrent > 2 ? 3 : 0);
            } else if (cspd < sec[4]) {
                accelpower(ctrlp, accel, cspd, dcurrent > 4 ? 3 : dcurrent > 3 ? 3 : 0);
            } else if (cspd < sec[5]) {
                accelpower(ctrlp, accel, cspd, dcurrent > 4 ? 3 : 0);
            }
        }
    }

    protected static boolean tcontains(String s, String tDataInfo) {
        return traindata.dataconfig.contains(tDataInfo + s);
    }

    protected static double gendefm(double decel, double cspd, double decelfr, int i1, int i2, int i3, int i4) {
        return globaldecel(decel, cspd, decelfr, i1, i2, i3, i4);
    }

    private static double globaldecel(double decel, double cspd, double decelfr, int i, int i2, int i3, int i4) {
        double added = 0;
        if (cspd < i) {
            added = -0.5;
        } else if (cspd < i2) {
            added = 0;
        } else if (cspd < i3) {
            added = 0.5;
        } else if (cspd < i4) {
            added = 1;
        } else if (cspd >= i4) {
            added = 1.5;
        }
        return decel * (decelfr - added) / 7;
    }

    protected static void accelpower(Player ctrlp, double accel, double cspeed, int i) {
        speed.put(ctrlp, cspeed + accel * i / 7);
    }
}