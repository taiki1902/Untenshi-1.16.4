package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.Set;

import static me.fiveave.untenshi.ato.*;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.events.doorControls;
import static me.fiveave.untenshi.main.stoppos;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signalName;

class motion {

    static void recursion1(CommandSender sender) {
        Player ctrlp = (Player) sender;
        if (playing.containsKey(ctrlp) && playing.get(ctrlp) && ctrlp.isInsideVehicle()) {
            recursion2(ctrlp);
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursion1(sender), interval);
        } else if (!ctrlp.isInsideVehicle() && !frozen.get(ctrlp)) {
            Bukkit.dispatchCommand(ctrlp, "uts activate false");
        } else if (frozen.get(ctrlp)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursion1(sender), interval);
        } else {
            mascon.put(ctrlp, -9);
            speed.put(ctrlp, 0.0);
        }
    }

    static void recursion2(Player p) {
        // From Config
        double accel = 0;
        double decel = 0;
        double ebdecel = 0;
        int[] speedsteps = new int[6];
        double oldspeed = speed.get(p);
        double speeddrop = plugin.getConfig().getDouble("speeddroprate");
        boolean stationstop = plugin.getConfig().getBoolean("stationsignstop");
        // Init train
        MinecartGroup mg = MinecartGroupStore.get(p.getVehicle());
        TrainProperties tprop = mg.getProperties();
        // From traindata (if available)
        String seltrainname = "";
        Set<String> allTrains = Objects.requireNonNull(traindata.dataconfig.getConfigurationSection("trains")).getKeys(false);
        // Choose most suitable type
        for (String tname : allTrains) {
            // Override config accels
            if (mg.getProperties().getDisplayName().contains(tname) && tname.length() > seltrainname.length()) {
                seltrainname = tname;
            }
        }
        // Set data accordingly
        if (seltrainname.length() > 0) {
            String tDataInfo = "trains." + seltrainname;
            if (traindata.dataconfig.contains(tDataInfo + ".accel"))
                accel = traindata.dataconfig.getDouble(tDataInfo + ".accel");
            if (tContains(".decel", tDataInfo))
                decel = traindata.dataconfig.getDouble(tDataInfo + ".decel");
            if (tContains(".traintype", tDataInfo))
                traintype.put(p, traindata.dataconfig.getString(tDataInfo + ".traintype"));
            if (tContains(".ebdecel", tDataInfo))
                ebdecel = traindata.dataconfig.getDouble(tDataInfo + ".ebdecel") * 2;
            if (tContains(".speeds", tDataInfo) && traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").size() == 6) {
                for (int i = 0; i < 6; i++) {
                    speedsteps[i] = traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").get(i);
                }
            }
        }
        // If cannot find most suitable type
        else {
            switch (traintype.get(p)) {
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
        }
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df2 = new DecimalFormat("#.##");
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.UP);
        // Electric current brake
        double currentnow = current.get(p);
        // Set current for current mascon
        double ecb = Integer.parseInt(df0.format(480.0 / 9 * mascon.get(p)));
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
        // Set real current
        if (ecb < currentnow) {
            current.put(p, (currentnow - ecb) > 80.0 / 3 ? currentnow - 80.0 / 3 : ecb);
        } else if (ecb > currentnow) {
            current.put(p, (ecb - currentnow) > 80.0 / 3 ? currentnow + 80.0 / 3 : ecb);
        }
        // If brake cancel accel
        if (currentnow > 0 && ecb < 0) {
            current.put(p, 0.0);
        }
        // Slope speed adjust (new physics testing in progress)
        Location headLoc = mg.head().getEntity().getLocation();
        Location tailLoc = mg.tail().getEntity().getLocation();
        double slopeaccel = getSlopeAccel(headLoc, tailLoc);
        // Accel and decel
        double stopdecel = decelSwitch(p, speed.get(p), speeddrop, decel, ebdecel, currentnow, speedsteps, slopeaccel);
        if (dooropen.get(p) == 0) {
            speed.put(p, speed.get(p)
                    + accelSwitch(accel, (int) (currentnow * 9 / 480), speed.get(p), speedsteps) / ticksin1s // Acceleration
                    - stopdecel / ticksin1s // Deceleration (speed drop included)
            );
        }
        // ATS Forced Controls
        if (mascon.get(p) == -9) {
            if (atsforced.get(p) == 2) {
                speed.put(p, speed.get(p) - decel);
            }
        } else {
            atsforced.put(p, 0);
        }
        // Ctrl Text
        String ctrltext = "";
        if (mascon.get(p) == -9) {
            ctrltext = ChatColor.DARK_RED + "EB";
        } else if (mascon.get(p) >= -8 && mascon.get(p) <= -1) {
            ctrltext = ChatColor.RED + "B" + Math.abs(mascon.get(p));
        } else if (mascon.get(p) == 0) {
            ctrltext = ChatColor.WHITE + "N";
        } else if (mascon.get(p) >= 1 && mascon.get(p) <= 5) {
            ctrltext = ChatColor.GREEN + "P" + mascon.get(p);
        }
        // Anti-negative speed and force stop when door is open
        if (speed.get(p) < 0 || dooropen.get(p) > 0) {
            speed.put(p, 0.0);
        }
        df3.setRoundingMode(RoundingMode.CEILING);
        // Cancel TC motion-related sign actions
        if (!stationstop) mg.getActions().clear();
        // Shock when stopping
        String shock = speed.get(p) == 0 && speed.get(p) < oldspeed ? " " + ChatColor.GRAY + df2.format(stopdecel) + " km/h/s" : "";
        // Combine properties and action bar
        double blockpertick = Double.parseDouble(df3.format(speed.get(p) / 72));
        tprop.setSpeedLimit(blockpertick);
        mg.setForwardForce(blockpertick);
        mg.setProperties(tprop);
        // Door (enter and exit train)
        if (doordiropen.get(p) && dooropen.get(p) < 30) {
            dooropen.put(p, dooropen.get(p) + 1);
        } else if (!doordiropen.get(p) && dooropen.get(p) > 0) {
            dooropen.put(p, dooropen.get(p) - 1);
        }
        boolean do30 = dooropen.get(p) == 30;
        if (!doorconfirm.get(p) && (dooropen.get(p) == 0 || do30)) {
            tprop.setPlayersEnter(do30);
            tprop.setPlayersExit(do30);
            doorconfirm.put(p, true);
        }
        // Door text
        String tolangtxt = doorconfirm.get(p) ? "ed" : "ing";
        String doortxt = doordiropen.get(p) ? (atostoptime.containsKey(p) ? ChatColor.GOLD + "..." + atostoptime.get(p) : ChatColor.GREEN + getlang("open" + tolangtxt)) : ChatColor.RED + getlang("clos" + tolangtxt);
        // Action bar
        String actionbarmsg = "" + ctrltext + ChatColor.WHITE + " | " + ChatColor.YELLOW + getlang("speed") + ChatColor.WHITE + df0.format(speed.get(p)) + " km/h" + " | " + ChatColor.YELLOW + getlang("points") + ChatColor.WHITE + points.get(p) + " | " + ChatColor.YELLOW + getlang("door") + doortxt;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Count points
        if (freemodeNoATO(p) && mascon.get(p) == -9 && speed.get(p) > 20 && !atsbraking.get(p) && !atsping.get(p)) {
            // Misuse EB
            if (deductdelay.get(p) >= 50) {
                deductdelay.put(p, 0);
                pointCounter(p, ChatColor.YELLOW, getlang("misuseeb"), -5, "");
            } else {
                deductdelay.put(p, deductdelay.get(p) + 1);
            }
        }
        // Get signal update when warn (if signal speed isn't same)
        catchSignalUpdate(p);
        // Types of speeding
        boolean isoverspeed0 = speed.get(p) > minSpeedLimit(p);
        boolean isoverspeed3 = speed.get(p) > minSpeedLimit(p) + 3 || signallimit.get(p) == 0;
        // ATS-P
        atsp(p, decel, ebdecel, speedsteps, speeddrop, mg, isoverspeed0, isoverspeed3);
        // ATC Auto Control
        atc(p, isoverspeed3);
        // Instant ATS / ATC if red light
        if (signallimit.get(p) == 0) {
            atsbraking.put(p, true);
            mascon.put(p, -9);
            current.put(p, -480.0);
        }
        // ATO (Must be placed after actions)
        atosys(p, accel, decel, ebdecel, speeddrop, speedsteps, mg);
        // Stop position
        if (reqstopping.get(p)) {
            // Get stop location
            double stopdist = distFormula(p.getLocation().getX(), stoppos.get(p)[0], p.getLocation().getZ(), stoppos.get(p)[2]);
            int stopdistcm = (int) (stopdist * 100);
            // Start Overrun
            if (stopdist < 1 && !overrun.get(p)) {
                overrun.put(p, true);
            }
            // Rewards and penalties
            // In station EB
            if (mascon.get(p) == -9 && !staeb.get(p) && !plugin.getConfig().getBoolean("allowebstop")) {
                staeb.put(p, true);
            }
            // In station accel
            if (mascon.get(p) >= 1 && !staaccel.get(p) && !fixstoppos.get(p) && !plugin.getConfig().getBoolean("allowreaccel")) {
                staaccel.put(p, true);
            }
            // Stop positions
            // <= 1 m
            if (stopdist <= 1.00 && speed.get(p) == 0) {
                // 25 cm
                if (stopdist <= 0.25) {
                    // Need to fix stop pos? If no then add points
                    showStopPos(p, "stopposperfect", stopdistcm, shock, 10);
                }
                // 50 cm
                else if (stopdist <= 0.50) {
                    showStopPos(p, "stopposgreat", stopdistcm, shock, 5);
                }
                // 1 m
                else {
                    showStopPos(p, "stopposgood", stopdistcm, shock, 3);
                }
                openDoorProcedure(p);
            }
            // < 50 m
            else if (stopdist < 50 && speed.get(p) == 0 && overrun.get(p) && !fixstoppos.get(p)) {
                fixstoppos.put(p, true);
                staaccel.put(p, false);
                if (freemodeNoATO(p)) {
                    pointCounter(p, ChatColor.YELLOW, getlang("stopposover"), Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    generalMsg(p, ChatColor.YELLOW, getlang("stopposover") + ChatColor.RED + Math.round(stopdist) + " m" + shock);
                }
            }
            // Cho-heta-dane!
            else if (stopdist >= 50 && overrun.get(p)) {
                if (freemodeNoATO(p)) {
                    if (speed.get(p) > 0) {
                        atsforced.put(p, 2);
                        mascon.put(p, -9);
                    } else {
                        generalMsg(p, ChatColor.RED, getlang("stopposseriousover"));
                        restoreinit(p);
                    }
                } else {
                    reqstopping.put(p, false);
                    overrun.put(p, false);
                }
            }
        }
        // Catch point <= 0 and end game
        if (freemodeNoATO(p) && points.get(p) <= 0) {
            generalMsg(p, ChatColor.RED, getlang("nopoints"));
            restoreinit(p);
        }
    }

    private static void catchSignalUpdate(Player p) {
        if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
            Sign warnsign = (Sign) p.getWorld().getBlockAt(lastsisign.get(p)).getState();
            String warnsi = warnsign.getLine(2).split(" ")[1];
            int warnsp = Integer.parseInt(warnsign.getLine(2).split(" ")[2]);
            // Detect difference (saved sign speed != sign speed now)
            if (warnsp != lastsisp.get(p)) {
                lastsisp.put(p, warnsp);
                String signalmsg = signalName(warnsi);
                // If red light
                if (atsbraking.get(p) && signallimit.get(p) == 0) {
                    // Remove lastsisign and lastsisp as need to detect further signal warnings
                    signallimit.put(p, warnsp);
                    lastsisign.remove(p);
                    lastsisp.remove(p);
                }
                String speedlimittxt = warnsp >= maxspeed ? getlang("nolimit") : warnsp + " km/h";
                generalMsg(p, ChatColor.YELLOW, getlang("signalchange") + signalmsg + ChatColor.GRAY + " " + speedlimittxt);
            }
        }
    }

    private static void atc(Player p, boolean isoverspeed3) {
        if (signaltype.get(p).equals("atc")) {
            if (!atsbraking.get(p) && isoverspeed3) {
                mascon.put(p, -8);
                pointCounter(p, ChatColor.RED, getlang("atcrun"), -5, "");
            }
            atsbraking.put(p, isoverspeed3);
        }
    }

    private static void atsp(Player p, double decel, double ebdecel, int[] speedsteps, double speeddrop, MinecartGroup mg, boolean isoverspeed0, boolean isoverspeed3) {
        if (signaltype.get(p).equals("ats")) {
            double lowerSpeed = minSpeedLimit(p);
            // 0.0625 from result of getting mg.head() y-location
            Location headLoc = mg.head().getEntity().getLocation();
            Location tailLoc = mg.tail().getEntity().getLocation();
            double slopeaccel = 0;
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double signaldist = Double.MAX_VALUE;
            double signaldistdiff = Double.MAX_VALUE;
            double speeddist = Double.MAX_VALUE;
            double speeddistdiff = Double.MAX_VALUE;
            double reqsidist;
            double reqspdist;
            double distnow = Double.MAX_VALUE;
            // Find either signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
                Location siLocForSlope = new Location(mg.getWorld(), lastsisign.get(p).getX(), lastsisign.get(p).getY() + cartYPosDiff, lastsisign.get(p).getZ());
                slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
                reqsidist = getReqdist(p, globalDecel(decel, speed.get(p), 6, speedsteps), lastsisp.get(p), slopeaccelsi, speeddrop);
                signaldist = distFormula(lastsisign.get(p).getX() + 0.5, headLoc.getX(), lastsisign.get(p).getZ() + 0.5, headLoc.getZ());
                signaldistdiff = signaldist - reqsidist;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p)) {
                Location spLocForSlope = new Location(mg.getWorld(), lastspsign.get(p).getX(), lastspsign.get(p).getY() + cartYPosDiff, lastspsign.get(p).getZ());
                slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
                reqspdist = getReqdist(p, globalDecel(decel, speed.get(p), 6, speedsteps), lastspsp.get(p), slopeaccelsp, speeddrop);
                speeddist = distFormula(lastspsign.get(p).getX() + 0.5, headLoc.getX(), lastspsign.get(p).getZ() + 0.5, headLoc.getZ());
                speeddistdiff = speeddist - reqspdist;
            }
            double priority = Math.min(signaldistdiff, speeddistdiff);
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p) && priority == signaldistdiff) {
                lowerSpeed = lastsisp.get(p);
                distnow = signaldist;
                slopeaccel = slopeaccelsi;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p) && priority == speeddistdiff) {
                lowerSpeed = lastspsp.get(p);
                distnow = speeddist;
                slopeaccel = slopeaccelsp;
            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            getAllReqdist(p, decel, ebdecel, speeddrop, speedsteps, lowerSpeed, reqdist, slopeaccel);
            // If no signal give it one
            lastsisp.putIfAbsent(p, maxspeed);
            // Actual controlling part
            // tempdist is for anti-ATS-run, stop at 1 m before 0 km/h signal
            boolean nextredlight = lastsisp.get(p).equals(0) && priority == signaldistdiff;
            double tempdist = nextredlight ? (distnow - 1 < 0 ? 0 : distnow - 1) : distnow;
            // Pattern run
            if (((tempdist < reqdist[8] && speed.get(p) > lowerSpeed + 3) || isoverspeed3) && !atsping.get(p)) {
                atsping.put(p, true);
                if (tempdist < reqdist[9]) {
                    mascon.put(p, -9);
                    pointCounter(p, ChatColor.RED, getlang("atspeb") + " ", -5, "");
                } else {
                    mascon.put(p, -8);
                    pointCounter(p, ChatColor.RED, getlang("atspb8") + " ", -5, "");
                }
            } else if (tempdist > reqdist[8] && !isoverspeed0 && lowerSpeed != 0) {
                atsping.put(p, false);
            }
            // Pattern near
            boolean pnear = (tempdist < reqdist[8] + speed1s(p) * 5 && speed.get(p) > lowerSpeed) || isoverspeed0;
            if (!atspnear.get(p) && pnear) {
                generalMsg(p, ChatColor.GOLD, getlang("atspnear"));
            }
            atspnear.put(p, pnear);
        }
    }

    static double getSlopeAccel(Location endpt, Location beginpt) {
        double height = beginpt.getY() - endpt.getY();
        double length = distFormula(endpt.getX(), beginpt.getX(), endpt.getZ(), beginpt.getZ());
        return 35.30394 * height / (Math.hypot(height, length)); // 3.6 * gravity
    }

    static double distFormula(double x1, double x2, double y1, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static void showStopPos(Player p, String stopposeval, int stopdistcm, String shock, int pts) {
        if (!fixstoppos.get(p)) {
            String s = " ";
            if (freemodeNoATO(p)) {
                s = " " + ChatColor.GREEN + "+" + pts + " ";
                points.put(p, points.get(p) + pts);
            }
            generalMsg(p, ChatColor.YELLOW, getlang(stopposeval) + s + ChatColor.GRAY + stopdistcm + " cm" + shock);
        }
    }

    static boolean freemodeNoATO(Player p) {
        return !freemode.get(p) && !atodest.containsKey(p);
    }


    // Reset values, open doors, reset ATO
    static void openDoorProcedure(Player ctrlp) {
        reqstopping.put(ctrlp, false);
        fixstoppos.put(ctrlp, false);
        doorControls(ctrlp, true);
        if (atospeed.containsKey(ctrlp)) {
            mascon.put(ctrlp, -8);
        }
        atodest.remove(ctrlp);
        atospeed.remove(ctrlp);
    }

    static double getReqdist(Player ctrlp, double decel, double lowerSpeed, double slopeaccel, double speeddrop) {
        return (Math.pow(speed.get(ctrlp) + slopeaccel / ticksin1s, 2) - Math.pow(lowerSpeed, 2)) / (7.2 * Math.max(decel - slopeaccel, speeddrop));
    }

    static double accelSwitch(double accel, int dcurrent, double cspd, int[] sec) {
        double retaccel = 0;
        if (dcurrent - 1 >= 0) {
            retaccel = accel * sec[dcurrent] / sec[5] * (1 - 0.5 * cspd / sec[5]);
            if (cspd > sec[dcurrent - 1]) {
                retaccel *= 1 - (cspd - sec[dcurrent - 1]) / (sec[dcurrent] - sec[dcurrent - 1]);
            }
        }
        if (retaccel < 0) {
            retaccel = 0;
        }
        return retaccel;
    }

    static double decelSwitch(Player ctrlp, double speednow, double speeddrop, double decel, double ebrate, double current, int[] speedsteps, double slopeaccel) {
        double retdecel = 0;
        if (current == 0) {
            retdecel = speeddrop;
        } else if (current < 0 && current > -480) {
            retdecel = globalDecel(decel, speednow, Math.abs(current * 9 / 480) + 1, speedsteps);
        } else if (current == -480) {
            if (!atsbraking.get(ctrlp) && signallimit.get(ctrlp) != 0) {
                retdecel = globalDecel(decel, speednow, ebrate, speedsteps);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                atsforced.put(ctrlp, 2);
            }
        }
        return retdecel - slopeaccel;
    }

    static double globalDecel(double decel, double speednow, double decelfr, int[] speedsteps) {
        // (1 / 98) = (1 / 7 / 14)
        return (speednow >= speedsteps[0]) ? (decel * decelfr * (15 - 4 * (speednow - speedsteps[0]) / (speedsteps[5] - speedsteps[0])) / 98) : (decel * decelfr * 15 / 98);
    }


    static int minSpeedLimit(Player p) {
        return Math.min(speedlimit.get(p), signallimit.get(p));
    }

    static boolean tContains(String s, String tDataInfo) {
        return traindata.dataconfig.contains(tDataInfo + s);
    }
}