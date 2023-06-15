package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static me.fiveave.untenshi.ato.*;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.events.doorControls;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.signalName;
import static me.fiveave.untenshi.signalsign.updateSignals;
import static me.fiveave.untenshi.speedsign.getSignFromLoc;
import static me.fiveave.untenshi.speedsign.getSignToRailOffset;

class motion {

    static void recursiveClock(untenshi ld) {
        if (ld.isPlaying() && ld.getP().isInsideVehicle()) {
            motionSystem(ld);
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClock(ld), interval);
        } else if (!ld.getP().isInsideVehicle() && !ld.isFrozen()) {
            restoreinit(ld);
        } else if (ld.isFrozen()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClock(ld), interval);
        } else {
            ld.setMascon(-9);
            ld.setSpeed(0);
        }
    }

    static void motionSystem(untenshi ld) {
        // From Config
        double accel = 0;
        double decel = 0;
        double ebdecel = 0;
        int[] speedsteps = new int[6];
        double oldspeed = ld.getSpeed();
        double speeddrop = plugin.getConfig().getDouble("speeddroprate");
        boolean stationstop = plugin.getConfig().getBoolean("stationsignstop");
        // Init train
        MinecartGroup mg = ld.getTrain();
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
        // Set as default if none
        if (seltrainname.equals("")) {
            seltrainname = "default";
        }
        // Set data accordingly
        String tDataInfo = "trains." + seltrainname;
        if (traindata.dataconfig.contains(tDataInfo + ".accel"))
            accel = traindata.dataconfig.getDouble(tDataInfo + ".accel");
        if (traindata.dataconfig.contains(tDataInfo + ".decel"))
            decel = traindata.dataconfig.getDouble(tDataInfo + ".decel");
        if (traindata.dataconfig.contains(tDataInfo + ".ebdecel"))
            ebdecel = traindata.dataconfig.getDouble(tDataInfo + ".ebdecel");
        if (traindata.dataconfig.contains(tDataInfo + ".speeds") && traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").size() == 6) {
            for (int i = 0; i < 6; i++) {
                speedsteps[i] = traindata.dataconfig.getIntegerList(tDataInfo + ".speeds").get(i);
            }
        }
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df2 = new DecimalFormat("#.##");
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.UP);
        // Electric current brake
        double currentnow = ld.getCurrent();
        // Set current for current mascon
        double ecb = Integer.parseInt(df0.format(480.0 / 9 * ld.getMascon()));
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
        // Set real current
        if (ecb < currentnow) {
            ld.setCurrent((currentnow - ecb) > 40 / 3.0 * interval ? currentnow - 40 / 3.0 * interval : ecb);
        } else if (ecb > currentnow) {
            ld.setCurrent((ecb - currentnow) > 40 / 3.0 * interval ? currentnow + 40 / 3.0 * interval : ecb);
        }
        // If brake cancel accel
        if (currentnow > 0 && ecb < 0) {
            ld.setCurrent(0);
        }
        // Slope speed adjust (new physics testing in progress)
        Location headLoc = mg.head().getEntity().getLocation();
        Location tailLoc = mg.tail().getEntity().getLocation();
        double slopeaccel = getSlopeAccel(headLoc, tailLoc);
        // Accel and decel
        double stopdecel = decelSwitch(ld, ld.getSpeed(), speeddrop, decel, ebdecel, currentnow, speedsteps, slopeaccel);
        if (ld.getDooropen() == 0) {
            ld.setSpeed(ld.getSpeed() + accelSwitch(accel, (int) (currentnow * 9 / 480), ld.getSpeed(), speedsteps) / ticksin1s // Acceleration
                    - stopdecel / ticksin1s) // Deceleration (speed drop included)
            ;
        }
        // ATS Forced Controls
        if (ld.getMascon() == -9) {
            if (ld.getAtsforced() == 2) {
                ld.setSpeed(ld.getSpeed() - ebdecel / ticksin1s * 45 / 7);
            }
        } else {
            ld.setAtsforced(0);
        }
        // Anti-negative speed and force stop when door is open
        if (ld.getSpeed() < 0 || ld.getDooropen() > 0) {
            ld.setSpeed(0.0);
        }
        df3.setRoundingMode(RoundingMode.CEILING);
        // Cancel TC motion-related sign actions
        if (!stationstop) mg.getActions().clear();
        // Shock when stopping
        String shock = ld.getSpeed() == 0 && ld.getSpeed() < oldspeed ? " " + ChatColor.GRAY + df2.format(stopdecel) + " km/h/s" : "";
        // Combine properties and action bar
        double blockpertick = Double.parseDouble(df3.format(ld.getSpeed() / 72));
        tprop.setSpeedLimit(blockpertick);
        mg.setForwardForce(blockpertick);
        mg.setProperties(tprop);
        String doortxt = doorLogic(ld, tprop);
        // Display speed
        String speedcolor = "";
        if (ld.isAtsping() || ld.isForcedbraking()) {
            speedcolor += ChatColor.RED;
        } else if (ld.isAtspnear()) {
            speedcolor += ChatColor.GOLD;
        } else {
            speedcolor += ChatColor.WHITE;
        }
        String displaySpeed = speedcolor + df0.format(ld.getSpeed());
        // Action bar
        String actionbarmsg = getCtrltext(ld) + ChatColor.WHITE + " | " + ChatColor.YELLOW + getlang("speed") + displaySpeed + ChatColor.WHITE + " km/h" + " | " + ChatColor.YELLOW + getlang("points") + ChatColor.WHITE + ld.getPoints() + " | " + ChatColor.YELLOW + getlang("door") + doortxt;
        ld.getP().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Get signal update when warn (if signal speed isn't same)
        catchSignalUpdate(ld);
        // Interlocking stuff
        interlocking(ld);
        // Types of speeding
        boolean isoverspeed0 = ld.getSpeed() > minSpeedLimit(ld);
        boolean isoverspeed3 = ld.getSpeed() > minSpeedLimit(ld) + 3 || ld.getSignallimit() == 0;
        // ATS-P or ATC
        safetySys(ld, decel, ebdecel, speedsteps, speeddrop, mg, isoverspeed0, isoverspeed3);
        // Instant ATS / ATC if red light
        if (ld.getSignallimit() == 0) {
            ld.setForcedbraking(true);
            ld.setMascon(-9);
            ld.setCurrent(-480.0);
        }
        // ATO (Must be placed after actions)
        atosys(ld, accel, decel, ebdecel, speeddrop, speedsteps, mg);
        // Stop position
        stopPos(ld, shock);
        // Catch point <= 0 and end game
        if (freemodeNoATO(ld) && ld.getPoints() <= 0) {
            generalMsg(ld.getP(), ChatColor.RED, getlang("nopoints"));
            restoreinit(ld);
        }
    }

    private static void interlocking(untenshi ld) {
        if (ld.getIlposlist() != null && ld.getIlposlist().length > 0) {
            Location[] oldpos = ld.getIlposlist();
            // Check conditions to change signal
            int furthestoccupied = oldpos.length - 1;
            boolean ispriority = true;
            for (int i = oldpos.length - 1; i >= 0; i--) {
                for (Player p2 : driver.keySet()) {
                    if (!p2.equals(ld.getP()) && driver.get(p2) != null) {
                        untenshi ld2 = driver.get(p2);
                        if (ld2.isPlaying()) {
                            // Check resettable sign
                            Location[] rssign2locs = ld2.getResettablesisign();
                            if (rssign2locs != null) {
                                for (Location location : rssign2locs) {
                                    // Resettable sign = 0 km/h
                                    if (oldpos[i].equals(location) && Objects.requireNonNull(getSignFromLoc(location)).getLine(2).split(" ")[2].equals("0")) {
                                        if (i < furthestoccupied) {
                                            furthestoccupied = i;
                                        }
                                    }
                                }
                            }
                            // Check interlocking
                            Location[] il2posoccupied = ld2.getIlposoccupied();
                            if (il2posoccupied != null) {
                                for (Location location : il2posoccupied) {
                                    // Occupied by interlocking route
                                    if (oldpos[i].equals(location)) {
                                        if (i < furthestoccupied) {
                                            furthestoccupied = i;
                                        }
                                    }
                                }
                            }
                            // Check for priority
                            Location[] il2poslist = ld2.getIlposlist();
                            if (il2posoccupied != null) {
                                for (int j = 0; j < il2poslist.length; j++) {
                                    // Fighting with other's interlocking route and not occupied
                                    if (oldpos[0].equals(il2poslist[0]) && ld.getIlenterqueuetime() > ld2.getIlenterqueuetime()) {
                                        ispriority = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Is first priority? If yes then continue, if no then wait
            if (ispriority) {
                // Occupy and set signals when ok
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
                int orderno = 1;
                // Set signs with new signal and speed
                for (int signno = furthestoccupied; signno >= 0; signno--) {
                    // settable: Sign to be set
                    Sign settable = getSignFromLoc(oldpos[signno]);
                    if (settable != null) {
                        updateSignals(settable, "set " + ptnsisi[orderno] + " " + ptnsisp[orderno]);
                        // Cannot exceed halfptnlen
                        if (orderno + 1 != halfptnlen) {
                            orderno++;
                        }
                    }
                }
                // Set as occupied
                Location[] newiloccupied = new Location[furthestoccupied];
                // Put in interlocking occupied
                System.arraycopy(oldpos, 0, newiloccupied, 0, furthestoccupied);
                ld.setIlposoccupied(newiloccupied);
            }
        }
    }

    private static String getCtrltext(untenshi ld) {
        String ctrltext = "";
        if (ld.getMascon() == -9) {
            ctrltext = ChatColor.DARK_RED + "EB";
        } else if (ld.getMascon() >= -8 && ld.getMascon() <= -1) {
            ctrltext = ChatColor.RED + "B" + Math.abs(ld.getMascon());
        } else if (ld.getMascon() == 0) {
            ctrltext = ChatColor.WHITE + "N";
        } else if (ld.getMascon() >= 1 && ld.getMascon() <= 5) {
            ctrltext = ChatColor.GREEN + "P" + ld.getMascon();
        }
        return ctrltext;
    }

    private static void stopPos(untenshi ld, String shock) {
        if (ld.isReqstopping()) {
            // Get stop location
            double stopdist = distFormula(ld.getP().getLocation().getX(), ld.getStoppos()[0], ld.getP().getLocation().getZ(), ld.getStoppos()[2]);
            int stopdistcm = (int) (stopdist * 100);
            // Start Overrun
            if (stopdist < 1 && !ld.isOverrun()) {
                ld.setOverrun(true);
            }
            // Rewards and penalties
            // In station EB
            if (ld.getMascon() == -9 && !ld.isStaeb() && !plugin.getConfig().getBoolean("allowebstop")) {
                ld.setStaeb(true);
            }
            // In station accel
            if (ld.getMascon() >= 1 && !ld.isStaaccel() && !ld.isFixstoppos() && !plugin.getConfig().getBoolean("allowreaccel")) {
                ld.setStaaccel(true);
            }
            // Stop positions
            // <= 1 m
            if (stopdist <= 1.00 && ld.getSpeed() == 0) {
                // 25 cm
                if (stopdist <= 0.25) {
                    // Need to fix stop pos? If no then add points
                    showStopPos(ld, "stopposperfect", stopdistcm, shock, 10);
                }
                // 50 cm
                else if (stopdist <= 0.50) {
                    showStopPos(ld, "stopposgreat", stopdistcm, shock, 5);
                }
                // 1 m
                else {
                    showStopPos(ld, "stopposgood", stopdistcm, shock, 3);
                }
                openDoorProcedure(ld);
            }
            // < 50 m
            else if (stopdist < 50 && ld.getSpeed() == 0 && ld.isOverrun() && !ld.isFixstoppos()) {
                ld.setFixstoppos(true);
                ld.setStaaccel(false);
                if (freemodeNoATO(ld)) {
                    pointCounter(ld, ChatColor.YELLOW, getlang("stopposover"), Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    generalMsg(ld.getP(), ChatColor.YELLOW, getlang("stopposover") + ChatColor.RED + Math.round(stopdist) + " m" + shock);
                }
            }
            // Cho-heta-dane!
            else if (stopdist >= 50 && ld.isOverrun()) {
                if (freemodeNoATO(ld)) {
                    if (ld.getSpeed() > 0) {
                        ld.setAtsforced(2);
                        ld.setMascon(-9);
                    } else {
                        generalMsg(ld.getP(), ChatColor.RED, getlang("stopposseriousover"));
                        restoreinit(ld);
                    }
                } else {
                    ld.setReqstopping(false);
                    ld.setOverrun(false);
                }
            }
        }
    }

    private static String doorLogic(untenshi ld, TrainProperties tprop) {
        // Door (enter and exit train)
        if (ld.isDoordiropen() && ld.getDooropen() < 3 * ticksin1s) {
            ld.setDooropen(ld.getDooropen() + 1);
        } else if (!ld.isDoordiropen() && ld.getDooropen() > 0) {
            ld.setDooropen(ld.getDooropen() - 1);
        }
        boolean fullyopen = ld.getDooropen() == 3 * ticksin1s;
        if (!ld.isDoorconfirm() && (ld.getDooropen() == 0 || fullyopen)) {
            tprop.setPlayersEnter(fullyopen);
            tprop.setPlayersExit(fullyopen);
            ld.setDoorconfirm(true);
        }
        // Door text
        String tolangtxt = ld.isDoorconfirm() ? "ed" : "ing";
        return ld.isDoordiropen() ? (ld.getAtostoptime() != -1 ? ChatColor.GOLD + "..." + ld.getAtostoptime() : ChatColor.GREEN + getlang("open" + tolangtxt)) : ChatColor.RED + getlang("clos" + tolangtxt);
    }

    private static void catchSignalUpdate(untenshi ld) {
        if (ld.getLastsisign() != null && ld.getLastsisp() != maxspeed) {
            Sign warnsign = (Sign) ld.getP().getWorld().getBlockAt(ld.getLastsisign()).getState();
            String warnsi = warnsign.getLine(2).split(" ")[1];
            int warnsp = Integer.parseInt(warnsign.getLine(2).split(" ")[2]);
            // Detect difference (saved sign speed != sign speed now)
            if (warnsp != ld.getLastsisp()) {
                ld.setLastsisp(warnsp);
                String signalmsg = signalName(warnsi);
                // If red light
                if (ld.isForcedbraking() && ld.getSignallimit() == 0) {
                    // Remove lastsisign and lastsisp as need to detect further signal warnings
                    ld.setSignallimit(warnsp);
                    ld.setLastsisign(null);
                    ld.setLastsisp(maxspeed);
                }
                String speedlimittxt = warnsp >= maxspeed ? getlang("nolimit") : warnsp + " km/h";
                generalMsg(ld.getP(), ChatColor.YELLOW, getlang("signalchange") + signalmsg + ChatColor.GRAY + " " + speedlimittxt);
            }
        }
    }

    private static void safetySys(untenshi ld, double decel, double ebdecel, int[] speedsteps, double speeddrop, MinecartGroup mg, boolean isoverspeed0, boolean isoverspeed3) {
        double lowerSpeed = minSpeedLimit(ld);
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
        if (ld.getLastsisign() != null && ld.getLastsisp() != maxspeed) {
            int[] getSiOffset = getSignToRailOffset(ld.getLastsisign(), mg.getWorld());
            Location siLocForSlope = new Location(mg.getWorld(), ld.getLastsisign().getX() + getSiOffset[0], ld.getLastsisign().getY() + getSiOffset[1] + cartYPosDiff, ld.getLastsisign().getZ() + getSiOffset[2]);
            slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
            reqsidist = getReqdist(ld, globalDecel(decel, ld.getSpeed(), 6, speedsteps), ld.getLastsisp(), slopeaccelsi, speeddrop);
            signaldist = distFormula(ld.getLastsisign().getX() + getSiOffset[0] + 0.5, headLoc.getX(), ld.getLastsisign().getZ() + getSiOffset[2] + 0.5, headLoc.getZ());
            signaldistdiff = signaldist - reqsidist;
        }
        if (ld.getLastspsign() != null && ld.getLastspsp() != maxspeed) {
            int[] getSpOffset = getSignToRailOffset(ld.getLastspsign(), mg.getWorld());
            Location spLocForSlope = new Location(mg.getWorld(), ld.getLastspsign().getX() + getSpOffset[0], ld.getLastspsign().getY() + getSpOffset[1] + cartYPosDiff, ld.getLastspsign().getZ() + getSpOffset[2]);
            slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
            reqspdist = getReqdist(ld, globalDecel(decel, ld.getSpeed(), 6, speedsteps), ld.getLastspsp(), slopeaccelsp, speeddrop);
            speeddist = distFormula(ld.getLastspsign().getX() + getSpOffset[0] + 0.5, headLoc.getX(), ld.getLastspsign().getZ() + getSpOffset[2] + 0.5, headLoc.getZ());
            speeddistdiff = speeddist - reqspdist;
        }
        double priority = Math.min(signaldistdiff, speeddistdiff);
        if (ld.getLastsisign() != null && ld.getLastsisp() != maxspeed && priority == signaldistdiff) {
            lowerSpeed = ld.getLastsisp();
            distnow = signaldist;
            slopeaccel = slopeaccelsi;
        }
        if (ld.getLastspsign() != null && ld.getLastspsp() != maxspeed && priority == speeddistdiff) {
            lowerSpeed = ld.getLastspsp();
            distnow = speeddist;
            slopeaccel = slopeaccelsp;
        }
        // Get brake distance (reqdist)
        double[] reqdist = new double[10];
        getAllReqdist(ld, decel, ebdecel, speeddrop, speedsteps, lowerSpeed, reqdist, slopeaccel);
        // Actual controlling part
        // tempdist is for anti-ATS-run, stop at 1 m before 0 km/h signal
        boolean nextredlight = ld.getLastsisp() == 0 && priority == signaldistdiff;
        double tempdist = nextredlight ? (distnow - 1 < 0 ? 0 : distnow - 1) : distnow;
        // Pattern run
        if (((tempdist < reqdist[8] && ld.getSpeed() > lowerSpeed + 3) || isoverspeed3) && !ld.isAtsping()) {
            ld.setAtsping(true);
            if (tempdist < reqdist[9]) {
                ld.setMascon(-9);
                pointCounter(ld, ChatColor.RED, ld.getSafetysystype().toUpperCase() + " " + getlang("peb") + " ", -5, "");
            } else {
                ld.setMascon(-8);
                pointCounter(ld, ChatColor.RED, ld.getSafetysystype().toUpperCase() + " " + getlang("pb8") + " ", -5, "");
            }
        } else if (tempdist - reqdist[8] > 1 && !isoverspeed0 && !ld.isForcedbraking()) {
            ld.setAtsping(false);
        }
        // Pattern near
        boolean pnear = (tempdist < reqdist[8] + speed1s(ld) * 5 && ld.getSpeed() > lowerSpeed) || isoverspeed0;
        if (!ld.isAtspnear() && pnear) {
            generalMsg(ld.getP(), ChatColor.GOLD, ld.getSafetysystype().toUpperCase() + " " + getlang("pnear"));
        }
        ld.setAtspnear(pnear);
    }

    static double getSlopeAccel(Location endpt, Location beginpt) {
        double height = beginpt.getY() - endpt.getY();
        double length = distFormula(endpt.getX(), beginpt.getX(), endpt.getZ(), beginpt.getZ());
        return 35.30394 * height / (Math.hypot(height, length)); // 3.6 * gravity
    }

    static double distFormula(double x1, double x2, double y1, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static void showStopPos(untenshi ld, String stopposeval, int stopdistcm, String shock, int pts) {
        if (!ld.isFixstoppos()) {
            String s = " ";
            if (freemodeNoATO(ld)) {
                s = " " + ChatColor.GREEN + "+" + pts + " ";
                ld.setPoints(ld.getPoints() + pts);
            }
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang(stopposeval) + s + ChatColor.GRAY + stopdistcm + " cm" + shock);
        }
    }

    static boolean freemodeNoATO(untenshi ld) {
        return !ld.isFreemode() && ld.getAtodest() == null;
    }


    // Reset values, open doors, reset ATO
    static void openDoorProcedure(untenshi ld) {
        ld.setReqstopping(false);
        ld.setFixstoppos(false);
        doorControls(ld, true);
        if (ld.getAtospeed() != -1) {
            ld.setMascon(-8);
        }
        ld.setAtodest(null);
        ld.setAtospeed(-1);
    }

    static double getReqdist(untenshi ld, double decel, double lowerSpeed, double slopeaccel, double speeddrop) {
        return (Math.pow(ld.getSpeed() + slopeaccel / ticksin1s, 2) - Math.pow(lowerSpeed, 2)) / (7.2 * Math.max(decel - slopeaccel, speeddrop));
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

    static double decelSwitch(untenshi ld, double speednow, double speeddrop, double decel, double ebdecel, double current, int[] speedsteps, double slopeaccel) {
        double retdecel = 0;
        if (current == 0) {
            retdecel = speeddrop;
        } else if (current < 0 && current > -480) {
            retdecel = globalDecel(decel, speednow, Math.abs(current * 9 / 480) + 1, speedsteps);
        } else if (current == -480) {
            if (!ld.isForcedbraking() && ld.getSignallimit() != 0) {
                retdecel = globalDecel(ebdecel, speednow, 7, speedsteps);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                ld.setAtsforced(2);
            }
        }
        return retdecel - slopeaccel;
    }

    static double globalDecel(double decel, double speednow, double decelfr, int[] speedsteps) {
        // (1 / 98) = (1 / 7 / 14)
        return (speednow >= speedsteps[0]) ? (decel * decelfr * (15 - 4 * (speednow - speedsteps[0]) / (speedsteps[5] - speedsteps[0])) / 98) : (decel * decelfr * 15 / 98);
    }


    static int minSpeedLimit(untenshi ld) {
        return Math.min(ld.getSpeedlimit(), ld.getSignallimit());
    }
}