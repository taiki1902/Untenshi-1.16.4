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
import static me.fiveave.untenshi.main.stoppos;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signalName;

class motion {

    static void recursion1(CommandSender sender) {
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

    static Integer getmascon(Player ctrlplayer) {
        return mascon.get(ctrlplayer);
    }

    static void recursion2(Player p) {
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
                accel = plugin.getConfig().getDouble("localaccelrate") * 4.68 / 3.5;
                decel = plugin.getConfig().getDouble("localdecelrate") * 3.6 / 3.5;
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
                if (traindata.dataconfig.contains(tDataInfo + ".accel"))
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
        double ecb = 0;
        double currentnow = current.get(p);
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df2 = new DecimalFormat("#.##");
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.UP);
        // Set current for current mascon
        if (getmascon(p) >= -9 && getmascon(p) <= 5) {
            ecb = Integer.parseInt(df0.format(480.0 / 9 * getmascon(p)));
        }
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
        // Set real current
        if (ecb < currentnow) {
            current.put(p, (currentnow - ecb) > 80.0 / 3 ? currentnow - 80 / 3 : ecb);
        } else if (ecb > currentnow) {
            current.put(p, (ecb - currentnow) > 80.0 / 3 ? currentnow + 80 / 3 : ecb);
        }
        int dcurrent = (int) (currentnow * 9 / 480);
        // Accel and decel
        // i1-4: Ref decelswitch
        int i1 = speedsteps[0];
        int i2 = speedsteps[1];
        int i3 = speedsteps[2];
        int i4 = speedsteps[3];
        double stopdecel = decelswitch(p, speeddrop, decel, dcurrent, speed.get(p), i1, i2, i3, i4, ebdecel);
        if (dooropen.get(p) == 0) {
            speed.put(p, speed.get(p)
                    + accelswitch(accel, dcurrent, speed.get(p), speedsteps) // Acceleration
                    - stopdecel // Deceleration
                    - speeddrop * 2 // Friction speed drop
            );
        }
        // Have speed drop even if no accel
        // ATS Forced Controls
        if (mascon.get(p) == -9) {
            if (atsforced.get(p) == 2) {
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
        // Slope speed adjust
        double trainy = p.getVehicle().getLocation().getY();
        if (lasty.get(p) - trainy > 0) {
            speed.put(p, speed.get(p) + 0.234919);
        } else if (lasty.get(p) - trainy < 0) {
            speed.put(p, speed.get(p) - 0.234919);
        }
        // Set speed to 0 when under 0
        if (speed.get(p) < 0) {
            speed.put(p, 0.0);
        }
        lasty.put(p, trainy);
        df3.setRoundingMode(RoundingMode.CEILING);
        // Set speed (Anti VictorXcraft beikeiyaroe)
        if (!stationstop) mg.getActions().clear();
        // ATO (Automatic train operation)
        atosys(p, accel, decel, ebdecel, speeddrop, dcurrent, speedsteps);
        // Shock when stopping
        String shock = speed.get(p) == 0 && speed.get(p) < oldspeed ? " " + ChatColor.GRAY + df2.format(stopdecel * 10) + " km/h/s" : "";
        // Combine properties and action bar
        double blockpertick = Double.parseDouble(df3.format(speed.get(p) / 72));
        tprop.setSpeedLimit(blockpertick);
        mg.setForwardForce(blockpertick);
        mg.setProperties(tprop);
        String actionbarmsg = "" + ctrltext + ChatColor.WHITE + " | " + ChatColor.YELLOW + getlang("speed") + ChatColor.WHITE + df0.format(speed.get(p)) + " km/h" + " | " + ChatColor.YELLOW + getlang("points") + ChatColor.WHITE + points.get(p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Count points
        if (freemodenoato(p) && getmascon(p) == -9 && speed.get(p) > 20 && !atsbraking.get(p)) {
            // Misuse EB
            if (deductdelay.get(p) >= 50) {
                deductdelay.put(p, 0);
                pointCounter(p, ChatColor.YELLOW, getlang("misuseeb"), -5, "");
            } else {
                deductdelay.put(p, deductdelay.get(p) + 1);
            }
        }
        // Get signal update when warn (if signal speed isn't same)
        if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
            Sign warnsign = (Sign) p.getWorld().getBlockAt(lastsisign.get(p)).getState();
            String warnsi = warnsign.getLine(2).split(" ")[1];
            int warnsp = Integer.parseInt(warnsign.getLine(2).split(" ")[2]);
            // Detect difference (saved sign speed != sign speed now)
            if (warnsp != lastsisp.get(p)) {
                lastsisp.put(p, warnsp);
                String signalmsg = "";
                signalmsg = signalName(warnsi, signalmsg);
                // If red light
                if (atsbraking.get(p) && signallimit.get(p) == 0) {
                    // Remove lastsisign and lastsisp as need to detect further signal warnings
                    signallimit.put(p, warnsp);
                    lastsisign.remove(p);
                    lastsisp.remove(p);
                }
                String speedlimittxt = warnsp >= 360 ? getlang("nolimit") : warnsp + " km/h";
                p.sendMessage(utshead + ChatColor.YELLOW + getlang("signalchange") + signalmsg + ChatColor.GRAY + " " + speedlimittxt);
            }
        }
        // ATS-P
        boolean isoverspeed0 = speed.get(p) > signallimit.get(p) || speed.get(p) > speedlimit.get(p);
        boolean isoverspeed3 = speed.get(p) > signallimit.get(p) + 3 || speed.get(p) > speedlimit.get(p) + 3;
        if (signaltype.get(p).equals("ats")) {
            double signaldist;
            double speeddist;
            double lowerSpeed;
            double shortestDist;
            speeddist = lastspsign.containsKey(p) ? distFormula(lastspsign.get(p).getX() + 0.5, p.getLocation().getX(), lastspsign.get(p).getZ() + 0.5, p.getLocation().getZ()) : Double.MAX_VALUE;
            signaldist = lastsisign.containsKey(p) ? distFormula(lastsisign.get(p).getX() + 0.5, p.getLocation().getX(), lastsisign.get(p).getZ() + 0.5, p.getLocation().getZ()) : Double.MAX_VALUE;
            shortestDist = Math.min(signaldist, speeddist);
            lowerSpeed = shortestDist == signaldist ? Math.min((lastsisp.containsKey(p) ? Math.min(signallimit.get(p), lastsisp.get(p)) : signallimit.get(p)), speedlimit.get(p)) : Math.min((lastspsp.containsKey(p) ? Math.min(signallimit.get(p), lastspsp.get(p)) : signallimit.get(p)), speedlimit.get(p));
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            reqdist[9] = getreqdist(p, 10 * globaldecel(decel, speed.get(p), ebdecel, i1, i2, i3, i4), lowerSpeed);
            for (int a = 5; a <= 8; a += 3) {
                reqdist[a] = getreqdist(p, 10 * globaldecel(decel, speed.get(p), a + 1, i1, i2, i3, i4), lowerSpeed);
            }
            // Pattern run
            if (((shortestDist < reqdist[8] && speed.get(p) > lowerSpeed + 3) || isoverspeed3) && !atsping.get(p)) {
                atsping.put(p, true);
                if ((lowerSpeed == 0 && shortestDist - reqdist[5] < 1 && shortestDist < 5) || signallimit.get(p) == 0) {
                    mascon.put(p, -9);
                    pointCounter(p, ChatColor.RED, getlang("atspeb") + " ", -5, "");
                } else {
                    mascon.put(p, -8);
                    pointCounter(p, ChatColor.RED, getlang("atspb8") + " ", -5, "");
                }
            } else if (shortestDist > reqdist[8] && !isoverspeed0) {
                atsping.put(p, false);
            }
            // Pattern near
            boolean pnear = shortestDist < reqdist[5] || isoverspeed0;
            if (!atspnear.get(p) && pnear) {
                p.sendMessage(utshead + ChatColor.GOLD + getlang("atspnear"));
            }
            atspnear.put(p, pnear);
        }
        // ATC Signal Speeding (not atsebing, speed limit is 0 or other +3 km/h), Speed limit speeding (+3 km/h)
        if (!atsbraking.get(p) && isoverspeed3 && signaltype.get(p).equals("atc")) {
            if (freemodenoato(p)) {
                pointCounter(p, ChatColor.YELLOW, getlang("signalspeeding"), -5, "");
            }
            p.sendMessage(utshead + ChatColor.RED + getlang("atcrun"));
        }
        // ATC Auto Control
        if (signaltype.get(p).equals("atc")) {
            if (signallimit.get(p) > 0) {
                if (!atsbraking.get(p) && isoverspeed3) {
                    atsbraking.put(p, true);
                    mascon.put(p, -8);
                } else if (!isoverspeed3) {
                    atsbraking.put(p, false);
                }
            }
        }
        // Instant ATS / ATC if red light
        if (signallimit.get(p) == 0) {
            atsbraking.put(p, true);
            mascon.put(p, -9);
            current.put(p, -480.0);
        }
        // Door (enter and exit train)
        if (doordiropen.get(p) && dooropen.get(p) < 30) {
            dooropen.put(p, dooropen.get(p) + 1);
        } else if (!doordiropen.get(p) && dooropen.get(p) > 0) {
            dooropen.put(p, dooropen.get(p) - 1);
        }
        if (!doorconfirm.get(p)) {
            if (dooropen.get(p) == 0) {
                tprop.setPlayersEnter(false);
                tprop.setPlayersExit(false);
                doorconfirm.put(p, true);
                p.sendMessage(utshead + ChatColor.YELLOW + getlang("door") + ChatColor.RED + getlang("closed"));
            } else if (dooropen.get(p) == 30) {
                tprop.setPlayersEnter(true);
                tprop.setPlayersExit(true);
                doorconfirm.put(p, true);
                p.sendMessage(utshead + ChatColor.YELLOW + getlang("door") + ChatColor.GREEN + getlang("opened"));
            }
        }
        // Stop position
        if (reqstopping.get(p)) {
            // Get stop location
            double stopdist = distFormula(p.getLocation().getX(), stoppos.get(p)[0], p.getLocation().getZ(), stoppos.get(p)[2]);
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
            if (getmascon(p) >= 1 && !staaccel.get(p) && !fixstoppos.get(p) && !plugin.getConfig().getBoolean("allowreaccel")) {
                staaccel.put(p, true);
            }
            // Stop positions
            // <= 2 m
            if (stopdist <= 2.00 && speed.get(p) == 0) {
                // 25 cm
                if (stopdist <= 0.25) {
                    // Need to fix stop pos? If no then add points
                    showstoppos(p, "stopposperfect", stopdistcm, shock, 10);
                }
                // 50 cm
                else if (stopdist <= 0.50) {
                    showstoppos(p, "stopposgreat", stopdistcm, shock, 5);
                }
                // 1 m
                else if (stopdist <= 1.00) {
                    showstoppos(p, "stopposgood", stopdistcm, shock, 3);
                }
                opendoorprocedure(p);
            }
            // < 50 m
            else if (stopdist < 50 && speed.get(p) == 0 && overrun.get(p) && !fixstoppos.get(p)) {
                fixstoppos.put(p, true);
                staaccel.put(p, false);
                if (freemodenoato(p)) {
                    pointCounter(p, ChatColor.YELLOW, getlang("stopposover"), Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    p.sendMessage(utshead + ChatColor.YELLOW + getlang("stopposover") + ChatColor.RED + Math.round(stopdist) + " m" + shock);
                }
            }
            // Cho-heta-dane!
            else if (stopdist >= 50 && overrun.get(p)) {
                if (freemodenoato(p)) {
                    if (speed.get(p) > 0) {
                        atsforced.put(p, 2);
                        mascon.put(p, -9);
                    } else {
                        p.sendMessage(utshead + ChatColor.RED + getlang("stopposseriousover"));
                        restoreinit(p);
                    }
                } else {
                    reqstopping.put(p, false);
                    overrun.put(p, false);
                }
            }
        }
        // Catch point <= 0 and end game
        if (freemodenoato(p) && points.get(p) <= 0) {
            p.sendMessage(utshead + ChatColor.RED + getlang("nopoints"));
            Bukkit.dispatchCommand(p, "uts activate false");
        }
    }

    static double distFormula(double x1, double x2, double y1, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static void showstoppos(Player p, String stopposeval, int stopdistcm, String shock, int pts) {
        if (!fixstoppos.get(p)) {
            String s = " ";
            if (freemodenoato(p)) {
                s = " " + ChatColor.GREEN + "+" + pts + " ";
                points.put(p, (points.get(p) + pts));
            }
            p.sendMessage(utshead + ChatColor.YELLOW + getlang(stopposeval) + s + ChatColor.GRAY + stopdistcm + " cm" + shock);
        }
    }

    static boolean freemodenoato(Player p) {
        return !freemode.get(p) && !atodest.containsKey(p);
    }


    // Reset values, open doors, reset ATO
    static void opendoorprocedure(Player ctrlp) {
        reqstopping.put(ctrlp, false);
        fixstoppos.put(ctrlp, false);
        doorControls(ctrlp, true);
        if (atospeed.containsKey(ctrlp)) {
            mascon.put(ctrlp, -8);
        }
        atodest.remove(ctrlp);
        atospeed.remove(ctrlp);
    }

    static double getreqdist(Player ctrlp, double decel, double lowerSpeed) {
        return (Math.pow(speed.get(ctrlp), 2) / (7.2 * decel)) - (Math.pow(lowerSpeed, 2) / (7.2 * decel));
    }

    static double decelswitch(Player ctrlp, double speeddrop, double decel, double dcurrent, double cspd, int i1, int i2, int i3, int i4, double ebrate) {
        double decelvalue = 0;
        if (dcurrent == 0) {
            if (speed.containsKey(ctrlp)) {
                decelvalue = speeddrop;
            }
        } else if (dcurrent < 0 && dcurrent > -9) {
            decelvalue = globaldecel(decel, cspd, Math.abs(dcurrent) + 1, i1, i2, i3, i4);
        } else if (dcurrent == -9) {
            if (!atsbraking.get(ctrlp) && signallimit.get(ctrlp) != 0) {
                decelvalue = globaldecel(decel, cspd, ebrate, i1, i2, i3, i4);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                atsforced.put(ctrlp, 2);
            }
        }
        return decelvalue;
    }

    static double globaldecel(double decel, double cspd, double decelfr, int i, int i2, int i3, int i4) {
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

    static double accelswitch(double accel, double dcurrent, double cspd, int[] sec) {
        double retaccel = 0;
        if (dcurrent > 0) {
            if (cspd < sec[0]) {
                retaccel = accelpower(accel, 7);
            } else if (cspd < sec[1]) {
                retaccel = accelpower(accel, dcurrent > 1 ? 6 : 3);
            } else if (cspd < sec[2]) {
                retaccel = accelpower(accel, dcurrent > 2 ? 5 : dcurrent > 1 ? 3 : 0);
            } else if (cspd < sec[3]) {
                retaccel = accelpower(accel, dcurrent > 3 ? 4 : dcurrent > 2 ? 3 : 0);
            } else if (cspd < sec[4]) {
                retaccel = accelpower(accel, dcurrent > 4 ? 3 : dcurrent > 3 ? 3 : 0);
            } else if (cspd < sec[5]) {
                retaccel = accelpower(accel, dcurrent > 4 ? 3 : 0);
            }
        }
        return retaccel;
    }

    static double accelpower(double accel, int i) {
        return accel * i / 7;
    }

    static boolean tcontains(String s, String tDataInfo) {
        return traindata.dataconfig.contains(tDataInfo + s);
    }
}