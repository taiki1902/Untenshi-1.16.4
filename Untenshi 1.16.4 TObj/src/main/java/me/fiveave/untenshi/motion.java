package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;

import java.math.RoundingMode;
import java.text.DecimalFormat;

import static me.fiveave.untenshi.ato.atosys;
import static me.fiveave.untenshi.ato.openDoorProcedure;
import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.events.trainSound;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.signalsign.*;
import static me.fiveave.untenshi.speedsign.getSignFromLoc;
import static me.fiveave.untenshi.speedsign.getSignToRailOffset;

class motion {

    static void recursiveClockLv(utsvehicle lv) {
        if (lv.getTrain() != null && !lv.getTrain().isEmpty()) {
            try {
                motionSystem(lv);
                Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClockLv(lv), tickdelay);
            } catch (Exception e) {
                e.printStackTrace();
                restoreinitlv(lv);
            }
        } else {
            restoreinitlv(lv);
        }
    }

    static void recursiveClockLd(utsdriver ld) {
        if (ld.isPlaying() && ld.getP().isInsideVehicle() && MinecartGroup.get(ld.getP().getVehicle()).equals(ld.getLv().getTrain())) {
            try {
                driverSystem(ld);
                Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClockLd(ld), tickdelay);
            } catch (Exception e) {
                e.printStackTrace();
                restoreinitld(ld);
            }
        } else if (!ld.getP().isInsideVehicle() && !ld.isFrozen()) {
            restoreinitld(ld);
        } else if (ld.isFrozen()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClockLd(ld), tickdelay);
        } else {
            restoreinitld(ld);
        }
    }

    static void motionSystem(utsvehicle lv) {
        // From Config
        double oldspeed = lv.getSpeed();
        boolean stationstop = plugin.getConfig().getBoolean("stationsignstop");
        // Init train
        MinecartGroup mg = lv.getTrain();
        TrainProperties tprop = mg.getProperties();
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df2 = new DecimalFormat("#.##");
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.UP);
        // Electric current brake
        double currentnow = lv.getCurrent();
        // Set current for current mascon
        double ecbtarget = Integer.parseInt(df0.format(getCurrentFromNotch(lv.getMascon())));
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
        // Set real current
        if (ecbtarget < currentnow) {
            lv.setCurrent((currentnow - ecbtarget) > 40 / 3.0 * tickdelay ? currentnow - 40 / 3.0 * tickdelay : ecbtarget);
            if (currentnow - 40 / 3.0 < 0 && currentnow - 40 / 3.0 > ecbtarget) {
                trainSound(lv, "brake_apply");
            }
            if (currentnow > 0 && currentnow - 40 / 3.0 > 0) {
                trainSound(lv, "accel_off");
            }
        } else if (ecbtarget > currentnow) {
            lv.setCurrent((ecbtarget - currentnow) > 40 / 3.0 * tickdelay ? currentnow + 40 / 3.0 * tickdelay : ecbtarget);
            if (currentnow < 0 && currentnow + 40 / 3.0 < 0) {
                trainSound(lv, "brake_release");
            }
            if (currentnow + 40 / 3.0 > 0 && currentnow + 40 / 3.0 < ecbtarget) {
                trainSound(lv, "accel_on");
            }
        }
        // If brake cancel accel
        if (currentnow > 0 && ecbtarget < 0) {
            lv.setCurrent(0);
        }
        // Slope speed adjust
        Location headLoc = mg.head().getEntity().getLocation();
        Location tailLoc = mg.tail().getEntity().getLocation();
        double slopeaccel = getSlopeAccel(headLoc, tailLoc);
        // Accel and decel
        double stopdecel = decelSwitch(lv, lv.getSpeed(), slopeaccel);
        if (lv.getDooropen() == 0) {
            lv.setSpeed(lv.getSpeed() + accelSwitch(lv, lv.getSpeed(), (int) (getNotchFromCurrent(currentnow))) / ticksin1s // Acceleration
                    - stopdecel / ticksin1s) // Deceleration (speed drop included)
            ;
        }
        // Anti-negative speed and force stop when door is open
        if (lv.getSpeed() < 0 || lv.getDooropen() > 0) {
            lv.setSpeed(0.0);
        }
        df3.setRoundingMode(RoundingMode.CEILING);
        // Cancel TC motion-related sign actions
        if (!stationstop) mg.getActions().clear();
        // Shock when stopping
        String shock = lv.getSpeed() == 0 && lv.getSpeed() < oldspeed ? " " + ChatColor.GRAY + df2.format(stopdecel) + " km/h/s" : "";
        // Combine properties and action bar
        double blockpertick = 0;
        try {
            blockpertick = Double.parseDouble(df3.format(lv.getSpeed() / 72));
        } catch (NumberFormatException ignored) {
        }
        tprop.setSpeedLimit(blockpertick);
        mg.setForwardForce(blockpertick);
        mg.setProperties(tprop);
        // Combine properties and action bar
        doorLogic(lv, tprop);
        // Get signal update when warn (if signal speed isn't same)
        catchSignalUpdate(lv);
        // Interlocking stuff
        interlocking(lv);
        // Types of speeding
        boolean isoverspeed0 = lv.getSpeed() > minSpeedLimit(lv);
        boolean isoverspeed3 = lv.getSpeed() > minSpeedLimit(lv) + 3 || lv.getSignallimit() == 0;
        // ATS-P or ATC
        safetySys(lv, mg, isoverspeed0, isoverspeed3);
        // ATO (Must be placed after actions)
        atosys(lv, mg);
        // Stop position
        stopPos(lv, shock);
    }

    static void driverSystem(utsdriver ld) {
        // Rounding
        DecimalFormat df3 = new DecimalFormat("#.###");
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
        df3.setRoundingMode(RoundingMode.CEILING);
        // Combine properties and action bar
        String doortxt = doorText(ld.getLv());
        // Display speed
        String speedcolor = "";
        if (ld.getLv().getAtsping() > 0 || ld.getLv().getAtsforced() == 2) {
            speedcolor += ChatColor.RED;
        } else if (ld.getLv().isAtspnear()) {
            speedcolor += ChatColor.GOLD;
        } else {
            speedcolor += ChatColor.WHITE;
        }
        String displaySpeed = speedcolor + df0.format(ld.getLv().getSpeed());
        // Action bar
        String actionbarmsg = getCtrltext(ld.getLv()) + ChatColor.WHITE + " | " + ChatColor.YELLOW + getlang("speed") + " " + displaySpeed + ChatColor.WHITE + " km/h" + " | " + ChatColor.YELLOW + getlang("points") + " " + ChatColor.WHITE + ld.getPoints() + " | " + ChatColor.YELLOW + getlang("door") + " " + doortxt;
        ld.getP().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Catch point <= 0 and end game
        if (noFreemodeOrATO(ld) && ld.getPoints() <= 0) {
            ebUntilRestoreInit(ld, getlang("nopoints"));
        }
    }

    private static void interlocking(utsvehicle lv) {
        if (lv.getIlposlist() != null && lv.getIlposlist().length > 0) {
            Location[] oldposlist = lv.getIlposlist();
            // Check conditions to change signal
            int furthestoccupied = oldposlist.length - 1;
            boolean ispriority = true;
            // mg2: other trains
            for (MinecartGroup mg2 : vehicle.keySet()) {
                if (!mg2.equals(lv.getTrain()) && vehicle.get(mg2) != null) {
                    utsvehicle lv2 = vehicle.get(mg2);
                    // Check resettable sign of other trains
                    Location[] rssign2locs = lv2.getRsposlist();
                    if (rssign2locs != null) {
                        signalOrderPtnResult result2 = getSignalOrderPtnResult(lv2);
                        // Check for each location
                        for (int i = oldposlist.length - 1; i >= 0; i--) {
                            for (int j = 0; j < rssign2locs.length; j++) {
                                Location location = rssign2locs[j];
                                // Maximum is result.halfptnlen - 1, cannot exceed (else index not exist and value will be null)
                                int minno = Math.min(result2.halfptnlen - 1, Math.max(0, j - lv2.getRsoccupiedpos()));
                                // Resettable sign signal of lv2 is supposed to be 0 km/h by resettable sign
                                if (oldposlist[i].equals(location) && result2.ptnsisp[minno] == 0) {
                                    if (i < furthestoccupied) {
                                        furthestoccupied = i;
                                    }
                                }
                            }
                        }
                    }
                    // Check occupied interlocking path of other trains
                    Location[] il2posoccupied = lv2.getIlposoccupied();
                    if (il2posoccupied != null) {
                        // Check for each location
                        for (int i = oldposlist.length - 1; i >= 0; i--) {
                            for (Location location : il2posoccupied) {
                                // Occupied by interlocking route
                                if (oldposlist[i].equals(location)) {
                                    if (i < furthestoccupied) {
                                        furthestoccupied = i;
                                    }
                                }
                            }
                        }
                    }
                    // Check for priority
                    // Fighting with other's unoccupied interlocking route
                    Location[] il2poslist = lv2.getIlposlist();
                    if (il2poslist != null) {
                        // Find location for start of blocked section, -1 means none
                        int blocked = -1;
                        for (int i = 0; i < oldposlist.length; i++) {
                            if (blocked == -1) {
                                for (int j = 0; j < il2poslist.length - 1; j++) {
                                    // Check for each location
                                    if (oldposlist[i].equals(il2poslist[j])) {
                                        blocked = i;
                                        break;
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                        if (blocked != -1) {
                            // Sign closer to this train
                            int firstsign = 0;
                            for (int i = 0; i <= blocked; i++) {
                                Sign test = getSignFromLoc(oldposlist[i]);
                                firstsign = i;
                                // If sign is found
                                if (test != null) {
                                    break;
                                }
                            }
                            // Sign closer to part being blocked
                            int lastsign = 0;
                            for (int i = blocked; i >= 0; i--) {
                                Sign test = getSignFromLoc(oldposlist[i]);
                                lastsign = i;
                                // If sign is found
                                if (test != null) {
                                    break;
                                }
                            }
                            // firstsign == lastsign means the front sign of train is last before blocked section
                            // Check priority
                            if (firstsign == lastsign && lv.getIlenterqueuetime() > lv2.getIlenterqueuetime() && lv.getIlpriority() <= lv2.getIlpriority()) {
                                ispriority = false;
                                break;
                            }
                        }
                    }
                }
            }
            // Is first priority? If yes then continue, if no then wait
            if (ispriority) {
                // Occupy and set signals when ok
                signalOrderPtnResult result = getSignalOrderPtnResult(lv);
                int orderno = 0;
                // Set signs with new signal and speed
                for (int signno = furthestoccupied; signno >= 0; signno--) {
                    // settable: Sign to be set
                    Sign settable = getSignFromLoc(oldposlist[signno]);
                    if (settable != null) {
                        updateSignals(settable, "set " + result.ptnsisi[orderno] + " " + result.ptnsisp[orderno]);
                        // Cannot exceed halfptnlen
                        if (orderno + 1 != result.halfptnlen) {
                            orderno++;
                        }
                    }
                }
                // Set as occupied
                Location[] newiloccupied = new Location[furthestoccupied];
                // Put in interlocking occupied
                System.arraycopy(oldposlist, 0, newiloccupied, 0, furthestoccupied);
                lv.setIlposoccupied(newiloccupied);
                // Delete other's resettable sign (check all locations to prevent bug)
                for (Location eachloc : newiloccupied) {
                    deleteOthersResettablesign(lv, eachloc);
                }
            }
        }
    }

    private static String getCtrltext(utsvehicle lv) {
        String ctrltext = "";
        if (lv.getMascon() == -9) {
            ctrltext = ChatColor.DARK_RED + "EB";
        } else if (lv.getMascon() >= -8 && lv.getMascon() <= -1) {
            ctrltext = ChatColor.RED + "B" + Math.abs(lv.getMascon());
        } else if (lv.getMascon() == 0) {
            ctrltext = ChatColor.WHITE + "N";
        } else if (lv.getMascon() >= 1 && lv.getMascon() <= 5) {
            ctrltext = ChatColor.GREEN + "P" + lv.getMascon();
        }
        return ctrltext;
    }

    private static void stopPos(utsvehicle lv, String shock) {
        if (lv.isReqstopping()) {
            // Get stop location
            double[] stopposloc = lv.getStoppos();
            double stopdist = distFormula(lv.getDriverseat().getEntity().getLocation().getX(), stopposloc[0], lv.getDriverseat().getEntity().getLocation().getZ(), stopposloc[2]);
            int stopdistcm = (int) (stopdist * 100);
            // Start Overrun
            if (stopdist < 1 && !lv.isOverrun()) {
                lv.setOverrun(true);
            }
            // Rewards and penalties
            // In station EB
            if (lv.getMascon() == -9 && !lv.isStaeb() && !plugin.getConfig().getBoolean("allowebstop")) {
                lv.setStaeb(true);
            }
            // In station accel
            if (lv.getMascon() >= 1 && !lv.isStaaccel() && !lv.isFixstoppos() && !plugin.getConfig().getBoolean("allowreaccel")) {
                lv.setStaaccel(true);
            }
            // Stop positions
            // <= 1 m
            if (stopdist <= 1.00 && lv.getSpeed() == 0) {
                // 25 cm
                if (stopdist <= 0.25) {
                    // Need to fix stop pos? If no then add points
                    showStopPos(lv, "stoppos_perfect", stopdistcm, shock, 10);
                }
                // 50 cm
                else if (stopdist <= 0.50) {
                    showStopPos(lv, "stoppos_great", stopdistcm, shock, 5);
                }
                // 1 m
                else {
                    showStopPos(lv, "stoppos_good", stopdistcm, shock, 3);
                }
                openDoorProcedure(lv);
            }
            // < 50 m
            else if (stopdist < 50 && lv.getSpeed() == 0 && lv.isOverrun() && !lv.isFixstoppos()) {
                lv.setFixstoppos(true);
                lv.setStaaccel(false);
                if (noFreemodeOrATO(lv.getLd())) {
                    pointCounter(lv.getLd(), ChatColor.YELLOW, getlang("stoppos_over") + " ", Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    generalMsg(lv.getLd(), ChatColor.YELLOW, getlang("stoppos_over") + " " + ChatColor.RED + Math.round(stopdist) + " m" + shock);
                }
            }
            // Cho-heta-dane!
            else if (stopdist >= 50 && lv.isOverrun()) {
                if (noFreemodeOrATO(lv.getLd())) {
                    ebUntilRestoreInit(lv.getLd(), getlang("stoppos_seriousover"));
                } else {
                    lv.setReqstopping(false);
                    lv.setOverrun(false);
                }
            }
        }
    }

    private static void ebUntilRestoreInit(utsdriver ld, String s) {
        if (ld != null) {
            if (ld.getLv().getAtsforced() != 2) {
                generalMsg(ld.getP(), ChatColor.RED, s);
            }
            ld.getLv().setAtsforced(2);
            ld.getLv().setMascon(-9);
            if (ld.getLv().getSpeed() == 0) {
                restoreinitld(ld);
            }
        }
    }

    private static void doorLogic(utsvehicle lv, TrainProperties tprop) {
        // Door (enter and exit train)
        if (lv.isDoordiropen() && lv.getDooropen() < 3 * ticksin1s) {
            lv.setDooropen(lv.getDooropen() + 1);
        } else if (!lv.isDoordiropen() && lv.getDooropen() > 0) {
            lv.setDooropen(lv.getDooropen() - 1);
        }
        boolean fullyopen = lv.getDooropen() == 3 * ticksin1s;
        if (!lv.isDoorconfirm() && (lv.getDooropen() == 0 || fullyopen)) {
            tprop.setPlayersEnter(fullyopen);
            tprop.setPlayersExit(fullyopen);
            lv.setDoorconfirm(true);
        }
    }

    private static String doorText(utsvehicle lv) {
        // Door text
        String tolangtxt = lv.isDoorconfirm() ? "ed" : "ing";
        return lv.isDoordiropen() ? (lv.getAtostoptime() != -1 ? ChatColor.GOLD + "..." + lv.getAtostoptime() : ChatColor.GREEN + getlang("door_open" + tolangtxt)) : ChatColor.RED + getlang("door_clos" + tolangtxt);
    }

    private static void catchSignalUpdate(utsvehicle lv) {
        if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed) {
            Sign warnsign = (Sign) lv.getTrain().getWorld().getBlockAt(lv.getLastsisign()).getState();
            String warnsi = warnsign.getLine(2).split(" ")[1];
            int warnsp = Integer.parseInt(warnsign.getLine(2).split(" ")[2]);
            // Detect difference (saved sign speed != sign speed now)
            if (warnsp != lv.getLastsisp()) {
                lv.setLastsisp(warnsp);
                String signalmsg = signalName(warnsi);
                // If red light
                if (lv.getAtsforced() == 2 && lv.getSignallimit() == 0) {
                    // Remove lastsisign and lastsisp as need to detect further signal warnings
                    lv.setSignallimit(warnsp);
                    lv.setLastsisign(null);
                    lv.setLastsisp(maxspeed);
                }
                String speedlimittxt = warnsp >= maxspeed ? getlang("speedlimit_del") : warnsp + " km/h";
                generalMsg(lv.getLd(), ChatColor.YELLOW, getlang("signal_change") + " " + signalmsg + ChatColor.GRAY + " " + speedlimittxt);
            }
        }
    }

    private static void safetySys(utsvehicle lv, MinecartGroup mg, boolean isoverspeed0, boolean isoverspeed3) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        double speeddrop = lv.getSpeeddrop();
        int[] speedsteps = lv.getSpeedsteps();
        double lowerSpeed = minSpeedLimit(lv);
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
        // TC forced stop (e.g. wait distance)
        if (lv.getAtsping() == 0 && lv.getAtsforced() != -1 && (mg.isObstacleAhead(Math.max(mg.getProperties().getWaitDistance(), 0), true, false) || mg.isObstacleAhead(0.001, false, true))) {
            lv.setAtsping(2);
            lv.setAtsforced(-1);
            lv.setMascon(-9);
            generalMsg(lv.getLd(), ChatColor.RED, getlang("tcblocking"));
        }
        if (lv.getAtsforced() == -1 && lv.getAtsping() > 0 && lv.getMascon() == -9) {
            lv.setSpeed(Math.max(lv.getSpeed() - ebdecel / ticksin1s * 45 / 7, 0));
        }
        // If no obstacle need braking in 2s then release
        if (lv.getAtsforced() == -1 && !mg.isObstacleAhead(mg.getProperties().getWaitDistance() + getThinkingDistance(lv, 8, 0, slopeaccel) * 2, true, true)) {
            lv.setAtsforced(0);
        }
        // Find either signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
        if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed) {
            int[] getSiOffset = getSignToRailOffset(lv.getLastsisign(), mg.getWorld());
            Location siLocForSlope = new Location(mg.getWorld(), lv.getLastsisign().getX() + getSiOffset[0], lv.getLastsisign().getY() + getSiOffset[1] + cartYPosDiff, lv.getLastsisign().getZ() + getSiOffset[2]);
            slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
            reqsidist = getReqdist(lv.getSpeed(), lv.getLastsisp(), avgRangeDecel(decel, lv.getSpeed(), lv.getLastsisp(), 6, speedsteps), slopeaccelsi, speeddrop);
            signaldist = distFormula(lv.getLastsisign().getX() + getSiOffset[0] + 0.5, headLoc.getX(), lv.getLastsisign().getZ() + getSiOffset[2] + 0.5, headLoc.getZ());
            signaldistdiff = signaldist - reqsidist;
        }
        if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed) {
            int[] getSpOffset = getSignToRailOffset(lv.getLastspsign(), mg.getWorld());
            Location spLocForSlope = new Location(mg.getWorld(), lv.getLastspsign().getX() + getSpOffset[0], lv.getLastspsign().getY() + getSpOffset[1] + cartYPosDiff, lv.getLastspsign().getZ() + getSpOffset[2]);
            slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
            reqspdist = getReqdist(lv.getSpeed(), lv.getLastspsp(), avgRangeDecel(decel, lv.getSpeed(), lv.getLastspsp(), 6, speedsteps), slopeaccelsp, speeddrop);
            speeddist = distFormula(lv.getLastspsign().getX() + getSpOffset[0] + 0.5, headLoc.getX(), lv.getLastspsign().getZ() + getSpOffset[2] + 0.5, headLoc.getZ());
            speeddistdiff = speeddist - reqspdist;
        }
        double priority = Math.min(signaldistdiff, speeddistdiff);
        if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed && priority == signaldistdiff) {
            lowerSpeed = lv.getLastsisp();
            distnow = signaldist;
            slopeaccel = slopeaccelsi;
        }
        if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed && priority == speeddistdiff) {
            lowerSpeed = lv.getLastspsp();
            distnow = speeddist;
            slopeaccel = slopeaccelsp;
        }
        // Get brake distance (reqdist)
        double[] reqdist = new double[10];
        getAllReqdist(lv, lv.getSpeed(), lowerSpeed, speeddrop, reqdist, slopeaccel, false);
        // Actual controlling part
        // Check if next is red light
        boolean nextredlight = lv.getLastsisp() == 0 && priority == signaldistdiff;
        // tempdist is for anti-ATS-run, stop at 1 m before 0 km/h signal
        double tempdist = nextredlight ? (distnow - 1 < 0 ? 0 : distnow - 1) : distnow;
        // Find minimum brake needed (default 10: even EB cannot brake in time)
        int reqbrake = 10;
        for (int b = 8; b >= 0; b--) {
            if (tempdist >= reqdist[b]) {
                reqbrake = b + 1;
            }
        }
        // Pattern run
        if (((reqbrake > 8 && lv.getSpeed() > lowerSpeed + 3) || isoverspeed3) && lv.getAtsping() == 0) {
            // Or SPAD (0 km/h signal) EB
            if (reqbrake > 9 || lv.getSignallimit() == 0) {
                lv.setMascon(-9);
                lv.setAtsping(2);
                pointCounter(lv.getLd(), ChatColor.RED, lv.getSafetysystype().toUpperCase() + " " + getlang("p_eb") + " ", -5, "");
            } else {
                lv.setMascon(-8);
                lv.setAtsping(1);
                pointCounter(lv.getLd(), ChatColor.RED, lv.getSafetysystype().toUpperCase() + " " + getlang("p_b8") + " ", -5, "");
            }
        } else if (lv.getSpeed() <= lowerSpeed + 3 && !isoverspeed0 && !isoverspeed3 && lv.getAtsforced() != 2 && lv.getAtsforced() != -1) {
            lv.setAtsping(0);
        }
        // Pattern near
        boolean pnear = (tempdist < reqdist[8] + speed1s(lv) * 5 && lv.getSpeed() > lowerSpeed) || isoverspeed0;
        if (!lv.isAtspnear() && pnear) {
            generalMsg(lv.getLd(), ChatColor.GOLD, lv.getSafetysystype().toUpperCase() + " " + getlang("p_near"));
        }
        lv.setAtspnear(pnear);
    }

    static void getAllReqdist(utsvehicle lv, double upperSpeed, double lowerSpeed, double speeddrop, double[] reqdist, double slopeaccel, boolean hasthinkingdist) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        int[] speedsteps = lv.getSpeedsteps();
        {
            double afterBrakeInitSpeed = getSpeedAfterBrakeInit(lv, upperSpeed, lowerSpeed, ebdecel, 9, slopeaccel);
            // Consider normal case or else EB will be too common (decelfr = 7 because no multiplier)
            // Need minimum is 0 or else there may be negative value
            double brakeInitDistance = Math.max(0, getReqdist(upperSpeed, afterBrakeInitSpeed, avgRangeDecel(ebdecel, upperSpeed, afterBrakeInitSpeed, 7, speedsteps), slopeaccel, speeddrop));
            double afterInitDistance = Math.max(0, getReqdist(afterBrakeInitSpeed, lowerSpeed, avgRangeDecel(ebdecel, afterBrakeInitSpeed, lowerSpeed, 7, speedsteps), slopeaccel, speeddrop));
            reqdist[9] = brakeInitDistance + afterInitDistance;
        }
        // Get speed drop distance
        reqdist[0] = Math.max(0, getReqdist(upperSpeed, lowerSpeed, speeddrop, slopeaccel, speeddrop));
        for (int a = 1; a <= 8; a++) {
            double afterBrakeInitSpeed = getSpeedAfterBrakeInit(lv, upperSpeed, lowerSpeed, decel, a, slopeaccel);
            // Need minimum is 0 or else there may be negative value
            double brakeInitDistance = Math.max(0, getReqdist(upperSpeed, afterBrakeInitSpeed, avgRangeDecel(decel, upperSpeed, afterBrakeInitSpeed, a + 1, speedsteps), slopeaccel, speeddrop));
            double afterInitDistance = Math.max(0, getReqdist(afterBrakeInitSpeed, lowerSpeed, avgRangeDecel(decel, afterBrakeInitSpeed, lowerSpeed, a + 1, speedsteps), slopeaccel, speeddrop));
            reqdist[a] = brakeInitDistance + afterInitDistance + (hasthinkingdist ? getThinkingDistance(lv, a, 0, slopeaccel) : 0);
        }
    }

    static double getReqdist(double upperSpeed, double lowerSpeed, double decel, double slopeaccel, double speeddrop) {
        return (Math.pow(upperSpeed + Math.max(slopeaccel - decel, 0) / 2, 2) - Math.pow(lowerSpeed, 2)) / (7.2 * Math.max(decel - slopeaccel, speeddrop));
    }

    static double getSpeedAfterBrakeInit(utsvehicle lv, double upperSpeed, double lowerSpeed, double decel, int a, double slopeaccel) {
        double speed = upperSpeed;
        double current = lv.getCurrent();

        while (current > getCurrentFromNotch(-a) && speed > lowerSpeed) {
            double thisdecel = (globalDecel(decel, speed, Math.abs(getNotchFromCurrent(current)) + 1, lv.getSpeedsteps()) - slopeaccel) / ticksin1s;
            if (speed - thisdecel > lowerSpeed) {
                speed -= thisdecel;
                current -= 40 / 3.0 * tickdelay;
            } else {
                break;
            }
        }
        return speed;
    }

    static double getCurrentFromNotch(int a) {
        return a * 480.0 / 9;
    }

    static double getNotchFromCurrent(double current) {
        return current * 9 / 480;
    }

    static double getThinkingTime(utsvehicle lv, int a) {
        // 1.0 / ticksin1s is necessary because of action delay
        return Math.max(1.0 / ticksin1s, Math.min(a * 0.2, (a + (getNotchFromCurrent(lv.getCurrent()))) * 0.2));
    }

    static double getThinkingDistance(utsvehicle lv, int a, double extra, double slopeaccel) {
        double t = getThinkingTime(lv, a) + extra;
        return t * (speed1s(lv) + slopeaccel / 3.6);
    }

    static double speed1s(utsvehicle lv) {
        return lv.getSpeed() / 3.6;
    }

    static double getSlopeAccel(Location endpt, Location beginpt) {
        double height = beginpt.getY() - endpt.getY();
        double length = distFormula(endpt.getX(), beginpt.getX(), endpt.getZ(), beginpt.getZ());
        return 35.30394 * height / (Math.hypot(height, length)); // 3.6 * gravity
    }

    static double distFormula(double x1, double x2, double y1, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static void showStopPos(utsvehicle lv, String stopposeval, int stopdistcm, String shock, int pts) {
        utsdriver ld = lv.getLd();
        if (!lv.isFixstoppos() && ld != null) {
            String s = " ";
            if (noFreemodeOrATO(lv.getLd())) {
                s = " " + ChatColor.GREEN + "+" + pts + " ";
                ld.setPoints(lv.getLd().getPoints() + pts);
            }
            generalMsg(ld.getP(), ChatColor.YELLOW, getlang(stopposeval) + s + ChatColor.GRAY + stopdistcm + " cm" + shock);
        }
    }

    static boolean noFreemodeOrATO(utsdriver ld) {
        if (ld != null) {
            return !ld.isFreemode() && ld.getLv().getAtodest() == null;
        } else {
            return true;
        }
    }

    static double accelSwitch(utsvehicle lv, double speed, int dcurrent) {
        double accel = lv.getAccel();
        int[] speedsteps = lv.getSpeedsteps();
        double retaccel = 0;
        if (dcurrent - 1 >= 0) {
            retaccel = accel * speedsteps[dcurrent] / speedsteps[5] * (1 - 0.5 * speed / speedsteps[5]);
            if (speed > speedsteps[dcurrent - 1]) {
                retaccel *= 1 - (speed - speedsteps[dcurrent - 1]) / (speedsteps[dcurrent] - speedsteps[dcurrent - 1]);
            }
        }
        if (retaccel < 0) {
            retaccel = 0;
        }
        return retaccel;
    }

    static double decelSwitch(utsvehicle lv, double speed, double slopeaccel) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        double speeddrop = lv.getSpeeddrop();
        double current = lv.getCurrent();
        int[] speedsteps = lv.getSpeedsteps();
        double retdecel = 0;
        if (current == 0) {
            retdecel = speeddrop;
        } else if (current < 0 && current > -480) {
            retdecel = globalDecel(decel, speed, Math.abs(getNotchFromCurrent(current)) + 1, speedsteps);
        } else if (current == -480) {
            if (lv.getAtsforced() != 2 && lv.getSignallimit() != 0) {
                retdecel = globalDecel(ebdecel, speed, 7, speedsteps);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                lv.setAtsforced(2);
                lv.setSpeed(lv.getSpeed() - ebdecel / ticksin1s * 45 / 7);
            }
        }
        return retdecel - slopeaccel;
    }

    static double globalDecel(double decel, double speed, double rate, int[] speedsteps) {
        // (1 / 98) = (1 / 7 / 14)
        return (speed >= speedsteps[0]) ? (decel * rate * (15 - 4 * (speed - speedsteps[0]) / (speedsteps[5] - speedsteps[0])) / 98) : (decel * rate * 15 / 98);
    }

    static double avgRangeDecel(double decel, double upperspd, double lowerspd, double rate, int[] speedsteps) {
        double k = decel * rate / (490 * (speedsteps[5] - speedsteps[0])) + 1; // result of da/dt / 20 + 1, or result of dividing decel of this tick to last tick
        double alpha = globalDecel(decel, upperspd, rate, speedsteps); // first term of geometric decel sequence, or current decel
        double varlower = Math.max(lowerspd, speedsteps[0]); // lower end speed in variable range in decel graph
        double x = Math.max(0, Math.log(-20 * (k - 1) * (varlower - upperspd) / alpha + 1) / Math.log(k)); // no. of ticks to lowerspeed / speedsteps[0]
        double sumdistvar = ((upperspd * x) - (alpha * (Math.pow(k, x) - 1 - x * (k - 1))) / (20 * Math.pow(k - 1, 2))) / 72; // braking distance in variable range
        double beta = globalDecel(decel, speedsteps[0], rate, speedsteps); // decel in static range
        double staticupper = Math.min(upperspd, speedsteps[0]); // upper end speed in static range in decel graph
        double sumdiststatic = Math.max(0, (Math.pow(staticupper, 2) - Math.pow(lowerspd, 2)) / (7.2 * beta)); // braking distance in static range
        // Need minimum is 0 or else there may be negative value
        return Math.max(0, upperspd - lowerspd > 0 ? (Math.pow(upperspd, 2) - Math.pow(lowerspd, 2)) / (7.2 * (sumdistvar + sumdiststatic)) : alpha);
    }

    static int minSpeedLimit(utsvehicle lv) {
        return Math.min(lv.getSpeedlimit(), lv.getSignallimit());
    }
}