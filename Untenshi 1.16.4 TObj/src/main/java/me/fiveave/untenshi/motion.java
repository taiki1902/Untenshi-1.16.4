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

import static me.fiveave.untenshi.ato.atoSys;
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
                restoreInitLv(lv);
            }
        } else {
            restoreInitLv(lv);
        }
    }

    static void recursiveClockLd(utsdriver ld) {
        if (ld.isPlaying() && ld.getP().isInsideVehicle() && MinecartGroup.get(ld.getP().getVehicle()).equals(ld.getLv().getTrain())) {
            try {
                driverSystem(ld);
                Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClockLd(ld), tickdelay);
            } catch (Exception e) {
                e.printStackTrace();
                restoreInitLd(ld);
            }
        } else if (!ld.getP().isInsideVehicle() && !ld.isFrozen()) {
            restoreInitLd(ld);
        } else if (ld.isFrozen()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recursiveClockLd(ld), tickdelay);
        } else {
            restoreInitLd(ld);
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
        DecimalFormat df0 = new DecimalFormat("#");
        df3.setRoundingMode(RoundingMode.CEILING);
        df0.setRoundingMode(RoundingMode.UP);
        // Electric current brake
        double currentnow = lv.getCurrent();
        // Set current for current mascon
        double ecbtarget = Integer.parseInt(df0.format(getCurrentFromNotch(lv.getMascon())));
        // Set real current
        if (ecbtarget < currentnow) {
            lv.setCurrent((currentnow - ecbtarget) > currentpertick ? currentnow - currentpertick : ecbtarget);
            if (currentnow - currentpertick < 0 && currentnow - currentpertick > ecbtarget) {
                trainSound(lv, "brake_apply");
            }
            if (currentnow > 0 && currentnow - currentpertick > 0) {
                trainSound(lv, "accel_off");
            }
        } else if (ecbtarget > currentnow) {
            lv.setCurrent((ecbtarget - currentnow) > currentpertick ? currentnow + currentpertick : ecbtarget);
            if (currentnow < 0 && currentnow + currentpertick < 0) {
                trainSound(lv, "brake_release");
            }
            if (currentnow + currentpertick > 0 && currentnow + currentpertick < ecbtarget) {
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
            // If door is closed
            lv.setSpeed(lv.getSpeed() + accelSwitch(lv, lv.getSpeed(), (int) (getNotchFromCurrent(currentnow))) * onetickins // Acceleration
                    - stopdecel * onetickins) // Deceleration (speed drop included)
            ;
        } else {
            // If door is open
            lv.setSpeed(0);
            if (lv.getMascon() > 0) {
                lv.setMascon(0);
            }
        }
        // Prevent negative speed
        if (lv.getSpeed() < 0) {
            lv.setSpeed(0.0);
        }
        // Cancel TC motion-related sign actions
        if (!stationstop) mg.getActions().clear();
        // Shock when stopping
        String shock = lv.getSpeed() == 0 && lv.getSpeed() < oldspeed ? " " + ChatColor.GRAY + String.format("%.2f km/h/s", stopdecel) : "";
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
        atoSys(lv, mg);
        // Stop position
        stopPos(lv, shock);
    }

    static void driverSystem(utsdriver ld) {
        // Rounding
        DecimalFormat df0 = new DecimalFormat("#");
        df0.setRoundingMode(RoundingMode.HALF_EVEN);
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
        String actionbarmsg = getCtrlText(ld.getLv()) + ChatColor.WHITE + " | " + ChatColor.YELLOW + getLang("speed") + " " + displaySpeed + ChatColor.WHITE + " km/h" + " | " + ChatColor.YELLOW + getLang("points") + " " + ChatColor.WHITE + ld.getPoints() + " | " + ChatColor.YELLOW + getLang("door") + " " + doortxt;
        ld.getP().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarmsg));
        // Catch point <= 0 and end game
        if (noFreemodeOrATO(ld) && ld.getPoints() <= 0) {
            ebUntilRestoreInit(ld, getLang("nopoints"));
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
                                if (oldposlist[i].equals(location) && i < furthestoccupied && result2.ptnsisp[minno] == 0) {
                                    furthestoccupied = i;
                                    break;
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
                                if (oldposlist[i].equals(location) && i < furthestoccupied) {
                                    furthestoccupied = i;
                                    break;
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
                            if (blocked != -1) {
                                break;
                            }
                            for (int j = 0; j < il2poslist.length - 1; j++) {
                                // Check for each location
                                if (oldposlist[i].equals(il2poslist[j])) {
                                    blocked = i;
                                    break;
                                }
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

    private static String getCtrlText(utsvehicle lv) {
        String ctrltext = "";
        int mascon = lv.getMascon();
        if (mascon == -9) {
            ctrltext = ChatColor.DARK_RED + "EB";
        } else if (mascon <= -1) {
            ctrltext = ChatColor.RED + "B" + -mascon;
        } else if (mascon == 0) {
            ctrltext = ChatColor.WHITE + "N";
        } else if (mascon <= 5) {
            ctrltext = ChatColor.GREEN + "P" + mascon;
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
                    pointCounter(lv.getLd(), ChatColor.YELLOW, getLang("stoppos_over") + " ", Math.toIntExact(-Math.round(stopdist)), shock);
                } else {
                    generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("stoppos_over") + " " + ChatColor.RED + Math.round(stopdist) + " m" + shock);
                }
            }
            // Cho-heta-dane!
            else if (stopdist >= 50 && lv.isOverrun()) {
                if (noFreemodeOrATO(lv.getLd())) {
                    ebUntilRestoreInit(lv.getLd(), getLang("stoppos_seriousover"));
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
                restoreInitLd(ld);
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
        return lv.isDoordiropen() ? (lv.getAtostoptime() != -1 ? ChatColor.GOLD + "..." + lv.getAtostoptime() : ChatColor.GREEN + getLang("door_open" + tolangtxt)) : ChatColor.RED + getLang("door_clos" + tolangtxt);
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
                String speedlimittxt = warnsp >= maxspeed ? getLang("speedlimit_del") : warnsp + " km/h";
                generalMsg(lv.getLd(), ChatColor.YELLOW, getLang("signal_change") + " " + signalmsg + ChatColor.GRAY + " " + speedlimittxt);
            }
        }
    }

    private static void safetySys(utsvehicle lv, MinecartGroup mg, boolean isoverspeed0, boolean isoverspeed3) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        double speeddrop = lv.getSpeeddrop();
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
            generalMsg(lv.getLd(), ChatColor.RED, getLang("tcblocking"));
        }
        if (lv.getAtsforced() == -1 && lv.getAtsping() > 0 && lv.getMascon() == -9) {
            lv.setSpeed(Math.max(lv.getSpeed() - ebdecel * onetickins * 45 / 7, 0));
        }
        // If no obstacle need braking in 2s then release
        if (lv.getAtsforced() == -1 && !mg.isObstacleAhead(mg.getProperties().getWaitDistance() + getThinkingDistance(lv, lv.getSpeed(), lowerSpeed, decel, 8, slopeaccel, 0) * 2, true, true)) {
            lv.setAtsforced(0);
        }
        // Find either signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
        if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed) {
            int[] getSiOffset = getSignToRailOffset(lv.getLastsisign(), mg.getWorld());
            Location siLocForSlope = new Location(mg.getWorld(), lv.getLastsisign().getX() + getSiOffset[0], lv.getLastsisign().getY() + getSiOffset[1] + cartyposdiff, lv.getLastsisign().getZ() + getSiOffset[2]);
            slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
            reqsidist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastsisp(), speeddrop, 6, slopeaccelsi, true, 0);
            signaldist = distFormula(lv.getLastsisign().getX() + getSiOffset[0] + 0.5, headLoc.getX(), lv.getLastsisign().getZ() + getSiOffset[2] + 0.5, headLoc.getZ());
            signaldistdiff = signaldist - reqsidist;
        }
        if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed) {
            int[] getSpOffset = getSignToRailOffset(lv.getLastspsign(), mg.getWorld());
            Location spLocForSlope = new Location(mg.getWorld(), lv.getLastspsign().getX() + getSpOffset[0], lv.getLastspsign().getY() + getSpOffset[1] + cartyposdiff, lv.getLastspsign().getZ() + getSpOffset[2]);
            slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
            reqspdist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastspsp(), speeddrop, 6, slopeaccelsp, true, 0);
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
        getAllReqdist(lv, lv.getSpeed(), lowerSpeed, speeddrop, reqdist, slopeaccel, false, 0);
        // Actual controlling part
        // Check if next is red light
        boolean nextredlight = lv.getLastsisp() == 0 && priority == signaldistdiff;
        // tempdist is for anti-ATS-run, stop at 1 m before 0 km/h signal
        double tempdist = nextredlight ? (distnow - 1 < 0 ? 0 : distnow - 1) : distnow;
        // Find minimum brake needed (default 10: even EB cannot brake in time)
        int reqbrake = 10;
        for (int b = 9; b >= 0; b--) {
            if (tempdist >= reqdist[b]) {
                reqbrake = b;
            }
        }
        // Pattern run
        if (((reqbrake > 8 && lv.getSpeed() > lowerSpeed + 3) || isoverspeed3) && lv.getAtsping() == 0) {
            // Or SPAD (0 km/h signal) EB
            if (reqbrake > 9 || lv.getSignallimit() == 0) {
                lv.setMascon(-9);
                lv.setAtsping(2);
                pointCounter(lv.getLd(), ChatColor.RED, lv.getSafetysystype().toUpperCase() + " " + getLang("p_eb") + " ", -5, "");
            } else {
                lv.setMascon(-8);
                lv.setAtsping(1);
                pointCounter(lv.getLd(), ChatColor.RED, lv.getSafetysystype().toUpperCase() + " " + getLang("p_b8") + " ", -5, "");
            }
        } else if (lv.getSpeed() <= lowerSpeed + 3 && !isoverspeed0 && !isoverspeed3 && lv.getAtsforced() != 2 && lv.getAtsforced() != -1) {
            lv.setAtsping(0);
        }
        // Pattern near
        boolean pnear = (tempdist < reqdist[8] + speed1s(lv) * 5 && lv.getSpeed() > lowerSpeed) || isoverspeed0;
        if (!lv.isAtspnear() && pnear) {
            generalMsg(lv.getLd(), ChatColor.GOLD, lv.getSafetysystype().toUpperCase() + " " + getLang("p_near"));
        }
        lv.setAtspnear(pnear);
    }

    static void getAllReqdist(utsvehicle lv, double upperSpeed, double lowerSpeed, double speeddrop, double[] reqdist, double slopeaccel, boolean hasthinkingdist, double extra) {
        for (int a = 0; a <= 9; a++) {
            reqdist[a] = getSingleReqdist(lv, upperSpeed, lowerSpeed, speeddrop, a, slopeaccel, hasthinkingdist, extra);
        }
    }

    static double getSingleReqdist(utsvehicle lv, double upperSpeed, double lowerSpeed, double speeddrop, int a, double slopeaccel, boolean hasthinkingdist, double extra) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        int[] speedsteps = lv.getSpeedsteps();
        if (a == 9) {
            double afterBrakeInitSpeed = getSpeedAfterBrakeInit(lv, upperSpeed, lowerSpeed, ebdecel, 9, slopeaccel);
            double brakeInitDistance = getThinkingDistance(lv, upperSpeed, lowerSpeed, ebdecel, 9, slopeaccel, hasthinkingdist ? extra : 0);
            // rate = 7 because no multiplier for avgRangeDecel
            double afterInitDistance = getReqdist(afterBrakeInitSpeed, lowerSpeed, avgRangeDecel(ebdecel, afterBrakeInitSpeed, lowerSpeed, 7, speedsteps), slopeaccel, speeddrop);
            return brakeInitDistance + afterInitDistance;
        } else if (a == 0) {
            // Get speed drop distance
            return getReqdist(upperSpeed, lowerSpeed, speeddrop, slopeaccel, speeddrop);
        } else {
            double afterBrakeInitSpeed = getSpeedAfterBrakeInit(lv, upperSpeed, lowerSpeed, decel, a, slopeaccel);
            double brakeInitDistance = getThinkingDistance(lv, upperSpeed, lowerSpeed, decel, a, slopeaccel, hasthinkingdist ? extra : 0);
            double afterInitDistance = getReqdist(afterBrakeInitSpeed, lowerSpeed, avgRangeDecel(decel, afterBrakeInitSpeed, lowerSpeed, a + 1, speedsteps), slopeaccel, speeddrop);
            return brakeInitDistance + afterInitDistance;
        }
    }

    static double getReqdist(double upperSpeed, double lowerSpeed, double decel, double slopeaccel, double speeddrop) {
        return Math.max((Math.pow(upperSpeed + Math.max(slopeaccel - decel, 0), 2) - Math.pow(lowerSpeed, 2)) / (7.2 * Math.max(decel - slopeaccel, speeddrop)), 0);
    }

    static double getSpeedAfterBrakeInit(utsvehicle lv, double upperSpeed, double lowerSpeed, double decel, int targetBrake, double slopeaccel) {
        double speed = upperSpeed;
        // Anti out-of-range causing GIGO
        double current = Math.min(0, lv.getCurrent());
        double targetcurrent = getCurrentFromNotch(-targetBrake);
        if (upperSpeed > lowerSpeed && current > targetcurrent) {
            AfterBrakeInitResult result = getAfterBrakeInitResult(lv, upperSpeed, lowerSpeed, decel, slopeaccel, current, targetcurrent);
            speed -= result.avgdecel * result.t; // result
        }
        return speed;
    }

    static double getThinkingDistance(utsvehicle lv, double upperSpeed, double lowerSpeed, double decel, int targetBrake, double slopeaccel, double extra) {
        // Prevent unable to accel just because near next target, but no need to brake or to neutral
        if (upperSpeed > lowerSpeed) {
            // Anti out-of-range causing GIGO
            double current = Math.min(0, lv.getCurrent());
            double targetcurrent = getCurrentFromNotch(-targetBrake);
            double sumdist = 0;
            if (current > targetcurrent) {
                AfterBrakeInitResult result = getAfterBrakeInitResult(lv, upperSpeed, lowerSpeed, decel, slopeaccel, current, targetcurrent);
                sumdist = (upperSpeed * result.t - result.avgdecel * Math.pow(result.t, 2) / 2) / 3.6; // get distance from basic decel distance formula, v = u*t+1/2*a*t^2, and speed to SI units
            }
            // Extra tick for action delay + slope acceleration considered (testing in progress)
            return sumdist + (upperSpeed + slopeaccel) / 3.6 * extra;
        } else {
            // Thinking distance not required
            return 0;
        }
    }

    static double getSpeedAfterPotentialAccel(utsvehicle lv, double currentSpeed, double slopeaccel) {
        double current = Math.max(0, lv.getCurrent());
        // 1 tick delay for compensation for action delay
        double speed = currentSpeed + (accelSwitch(lv, currentSpeed, (int) (getNotchFromCurrent(current))) + slopeaccel) * onetickins;
        // Anti out-of-range causing GIGO
        // Use while loop because using formula is tedious
        while (current > 0) {
            speed += (accelSwitch(lv, speed, (int) (getNotchFromCurrent(current))) + slopeaccel) * onetickins;
            current -= currentpertick;
        }
        return speed;
    }

    static double getCurrentFromNotch(int a) {
        return a * 480.0 / 9;
    }

    static double getNotchFromCurrent(double current) {
        return current * 9 / 480;
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
            generalMsg(ld.getP(), ChatColor.YELLOW, getLang(stopposeval) + s + ChatColor.GRAY + stopdistcm + " cm" + shock);
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
            retdecel = globalDecel(decel, speed, -getNotchFromCurrent(current) + 1, speedsteps);
        } else if (current == -480) {
            if (lv.getAtsforced() != 2 && lv.getSignallimit() != 0) {
                retdecel = globalDecel(ebdecel, speed, 7, speedsteps);
            } else {
                // SPAD ATS EB (-35 km/h/s)
                lv.setAtsforced(2);
                lv.setSpeed(lv.getSpeed() - ebdecel * onetickins * 45 / 7);
            }
        }
        return retdecel - slopeaccel;
    }

    static double globalDecel(double decel, double speed, double rate, int[] speedsteps) {
        // (1 / 98) = (1 / 7 / 14)
        return (speed >= speedsteps[0]) ? (decel * rate * (15 - 4 * (speed - speedsteps[0]) / (speedsteps[5] - speedsteps[0])) / 98) : (decel * rate * 15 / 98);
    }

    static double avgRangeDecel(double decel, double upperspd, double lowerspd, double rate, int[] speedsteps) {
        double alpha = globalDecel(decel, upperspd, rate, speedsteps); // first term of geometric decel sequence, or current decel
        if (upperspd > lowerspd && upperspd > speedsteps[0]) {
            double k = decel * rate / (490 * (speedsteps[5] - speedsteps[0])) + 1; // result of da/dx / 20 + 1, or result of dividing decel of this tick to last tick
            double varlower = Math.max(lowerspd, speedsteps[0]); // lower end speed in variable range in decel graph
            double x = Math.max(0, Math.log(20 * (k - 1) * (upperspd - varlower) / alpha + 1) / Math.log(k)); // no. of ticks to lowerspeed / speedsteps[0]
            double sumdistvar = ((upperspd * x) - (alpha * (Math.pow(k, x) - 1 - x * (k - 1))) / (20 * Math.pow(k - 1, 2))) / 72; // braking distance in variable range
            double beta = globalDecel(decel, speedsteps[0], rate, speedsteps); // decel in static range
            double staticupper = Math.min(upperspd, speedsteps[0]); // upper end speed in static range in decel graph
            double sumdiststatic = Math.max(0, (Math.pow(staticupper, 2) - Math.pow(lowerspd, 2)) / (7.2 * beta)); // braking distance in static range
            // Need minimum is 0 or else there may be negative value
            return Math.max(0, (Math.pow(upperspd, 2) - Math.pow(lowerspd, 2)) / (7.2 * (sumdistvar + sumdiststatic)));
        } else {
            return alpha;
        }
    }

    static int minSpeedLimit(utsvehicle lv) {
        return Math.min(lv.getSpeedlimit(), lv.getSignallimit());
    }

    static AfterBrakeInitResult getAfterBrakeInitResult(utsvehicle lv, double upperSpeed, double lowerSpeed, double decel, double slopeaccel, double current, double targetcurrent) {
        double ticksfrom0 = -current / currentpertick;
        double ticksatend = -targetcurrent / currentpertick;
        double avgrate = current < 0 ? ((80 * (ticksatend + ticksfrom0) * onetickins + 27) / 35) : ((80 * (Math.pow(ticksatend, 2) - 1) * onetickins + 27 * (ticksatend - 1)) / 35 / (ticksatend)); // average rate by mean value theorem, separate cases for current < 0 or not
        double avgdecel = avgRangeDecel(decel, upperSpeed, lowerSpeed, avgrate, lv.getSpeedsteps()) - slopeaccel; // gives better estimation than globalDecel, inaccuracy is negligible?
        // Time in s instead of tick to brake init end, but to prevent over-estimation and negative deceleration values
        double t = Math.min((ticksatend - ticksfrom0) * onetickins, avgdecel > 0 ? (upperSpeed - lowerSpeed) / avgdecel : Double.MAX_VALUE);
        return new AfterBrakeInitResult(avgdecel, t);
    }

    static class AfterBrakeInitResult {
        public final double avgdecel;
        public final double t;

        public AfterBrakeInitResult(double avgdecel, double t) {
            this.avgdecel = avgdecel;
            this.t = t;
        }
    }
}