package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import static me.fiveave.untenshi.cmds.generalMsg;
import static me.fiveave.untenshi.events.doorControls;
import static me.fiveave.untenshi.events.toEB;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.motion.*;
import static me.fiveave.untenshi.speedsign.getSignActualRefPos;

class ato {

    static void atoSys(utsvehicle lv, MinecartGroup mg) {
        double speeddrop = lv.getSpeeddrop();
        if (lv.getAtodest() != null && lv.getAtospeed() != -1 && lv.getAtsping() == 0 && lv.getAtsforced() == 0 && (lv.getLd() == null || lv.getLd().isAllowatousage())) {
            /*
             Get distances (distnow: smaller value of atodist and signaldist)
             reqatodist rate must be higher than others to prevent ATS-P or ATC run
            */
            double lowerSpeed = lv.getAtospeed();
            double decel = lv.getDecel();
            // 0.0625 from result of getting mg.head() y-location
            HeadAndTailResult result = getHeadAndTailResult(mg);
            Location actualAtoRefPos = getSignActualRefPos(lv.getAtodest(), mg.getWorld());
            double slopeaccelnow = getSlopeAccel(result.headLoc, result.tailLoc);
            double slopeaccelsel = getSlopeAccel(actualAtoRefPos, result.tailLoc);
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double reqatodist = getSingleReqdist(lv, lv.getSpeed(), lv.getAtospeed(), speeddrop, 6, slopeaccelsel, 0) + getThinkingDistance(lv, lv.getSpeed(), lv.getAtospeed(), decel, 6, slopeaccelsel, 0);
            double signaldist = Double.MAX_VALUE;
            double signaldistdiff = Double.MAX_VALUE;
            double speeddist = Double.MAX_VALUE;
            double speeddistdiff = Double.MAX_VALUE;
            double atodist = distFormula(actualAtoRefPos, result.headLoc);
            double atodistdiff = atodist - reqatodist;
            double reqsidist;
            double reqspdist;
            double distnow = atodist;
            int currentlimit = minSpeedLimit(lv);
            int finalmascon = 0;
            int finalbrake = 0;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed) {
                Location actualSiRefPos = getSignActualRefPos(lv.getLastsisign(), mg.getWorld());
                slopeaccelsi = getSlopeAccel(actualSiRefPos, result.tailLoc);
                reqsidist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastsisp(), speeddrop, 6, slopeaccelsi, 0)
                        + getThinkingDistance(lv, lv.getSpeed(), lv.getLastsisp(), decel, 6, slopeaccelsi, 0);
                signaldist = distFormula(actualSiRefPos, result.headLoc);
                signaldistdiff = signaldist - reqsidist;
            }
            if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed) {
                Location actualSpRefPos = getSignActualRefPos(lv.getLastspsign(), mg.getWorld());
                slopeaccelsp = getSlopeAccel(actualSpRefPos, result.tailLoc);
                reqspdist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastspsp(), speeddrop, 6, slopeaccelsp, 0)
                        + getThinkingDistance(lv, lv.getSpeed(), lv.getLastspsp(), decel, 6, slopeaccelsp, 0);
                speeddist = distFormula(actualSpRefPos, result.headLoc);
                speeddistdiff = speeddist - reqspdist;
            }
            double priority = (atodistdiff < signaldistdiff) ? (Math.min(atodistdiff, speeddistdiff)) : (Math.min(signaldistdiff, speeddistdiff));
            if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed && priority == signaldistdiff) {
                lowerSpeed = lv.getLastsisp();
                distnow = signaldist;
                slopeaccelsel = slopeaccelsi;
            }
            if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed && priority == speeddistdiff) {
                lowerSpeed = lv.getLastspsp();
                distnow = speeddist;
                slopeaccelsel = slopeaccelsp;
            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            // Potential speed after acceleration (acceleration after P5 to N)
            double potentialspeed = getSpeedAfterPotentialAccel(lv, lv.getSpeed(), slopeaccelnow);
            // To prevent redundant setting of mascon to N when approaching any signal
            boolean nextredlight = lv.getLastsisp() == 0 && priority == signaldistdiff;
            // tempdist is for anti-ATS-run, stop at 1 m before 0 km/h signal
            double tempdist = nextredlight ? (distnow - 1 < 0 ? 0 : distnow - 1) : distnow;
            // Actual controlling part, extra tick to prevent huge shock on stopping
            getAllReqdist(lv, lv.getSpeed(), lowerSpeed, speeddrop, reqdist, slopeaccelsel, onetickins);
            // Require accel? (no need to prepare for braking for next object and ATO target destination + additional thinking distance)
            boolean allowaccel = (lv.getMascon() > 0 || currentlimit - lv.getSpeed() > 5 && (lowerSpeed - lv.getSpeed() > 5 || tempdist > speed1s(lv)) && lv.getBrake() == 0) // 5 km/h under speed limit / target speed or already accelerating
                    && potentialspeed <= currentlimit // Will not go over speed limit
                    && (potentialspeed <= lowerSpeed || tempdist > speed1s(lv)) // Will not go over target speed
                    && !lv.isOverrun() // Not overrunning
                    && lv.getDooropen() == 0 && lv.isDoorconfirm(); // Doors closed
            boolean notnearreqdist = tempdist > reqdist[6] + getThinkingDistance(lv, potentialspeed, lowerSpeed, decel, 6, slopeaccelsel, 3);
            if (notnearreqdist && allowaccel) {
                finalmascon = 5;
            }
            // Require braking? (with additional thinking time, non-negative slope acceleration considered)
            if (tempdist < reqdist[6] + (lv.getSpeed() + Math.max(slopeaccelsel, 0)) / 3.6 * onetickins) {
                lv.setAtoforcebrake(true);
            }
            // Direct pattern or forced?
            if (lv.isAtoforcebrake() || lv.isAtopisdirect()) {
                // If even emergency brake cannot brake in time
                finalbrake = 9;
                // For more accurate result (prevent EB misuse especially at low speeds when smaller brakes can do)
                double minreqdist = Double.MAX_VALUE;
                for (int b = 9; b >= 0; b--) {
                    if (tempdist >= reqdist[b] || reqdist[b] < minreqdist) {
                        finalbrake = b;
                        minreqdist = reqdist[b];
                    }
                }
            }
            // Cancel braking? (Slope acceleration considered)
            if (tempdist > reqdist[6] + getThinkingDistance(lv, lv.getSpeed() + slopeaccelsel, lowerSpeed, decel, 6, slopeaccelsel, 3) && !lv.isOverrun()) {
                lv.setAtoforcebrake(false);
            }
            // Red light waiting procedure
            if (nextredlight && lv.getSpeed() == 0) {
                waitDepart(lv);
            }
            // Large brake application too common when not needed
            // Slightly speeding auto braking (not related to ATS-P or ATC)
            if (lv.getSpeed() + slopeaccelnow > currentlimit) {
                // Redefine reqdist (here for braking distance to speed limit)
                getAllReqdist(lv, lv.getSpeed() + slopeaccelnow, currentlimit, speeddrop, reqdist, slopeaccelnow, 0);
                int thisfinalbrake = 8;
                for (int a = 8; a >= 1; a--) {
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (speed1s(lv) >= reqdist[a]) {
                        thisfinalbrake = a;
                    }
                }
                if (finalbrake < thisfinalbrake) {
                    finalbrake = thisfinalbrake;
                }
            }
            // Final value
            lv.setMascon(finalmascon);
            lv.setBrake(finalbrake);
            // EB when overrun
            if (lv.isOverrun() && atodist > 1) {
                toEB(lv);
            }
        } else if (lv.getAtodest() != null && lv.getAtospeed() != -1 && lv.getLd() != null && !lv.getLd().isAllowatousage()) {
            lv.setAtodest(null);
            lv.setAtospeed(-1);
            generalMsg(lv.getLd().getP(), ChatColor.GOLD, getLang("ato_patterncancel"));
        }
    }

    // Reset values, open doors, reset ATO
    static void openDoorProcedure(utsvehicle lv) {
        lv.setReqstopping(false);
        lv.setFixstoppos(false);
        doorControls(lv, true);
        if (lv.getAtospeed() != -1) {
            lv.setMascon(0);
            lv.setBrake(8);
        }
        lv.setAtodest(null);
        lv.setAtospeed(-1);
    }

    // ATO Stop Time Countdown
    static void atoDepartCountdown(utsvehicle lv) {
        if (lv.getAtostoptime() != -1) {
            if (lv.getAtostoptime() > 0 && lv.isDoordiropen()) {
                lv.setAtostoptime(lv.getAtostoptime() - 1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> atoDepartCountdown(lv), 20);
            } else {
                doorControls(lv, false);
                // Reset values in order to depart
                lv.setAtostoptime(-1);
                lv.setAtodest(null);
                lv.setAtospeed(-1);
                waitDepart(lv);
            }
        }
    }

    private static void waitDepart(utsvehicle lv) {
        if (lv != null && lv.getTrain() != null && lv.getDriverseat().getEntity() != null) {
            boolean notindist = true;
            double[] reqdist = new double[10];
            getAllReqdist(lv, minSpeedLimit(lv), 0, lv.getSpeeddrop(), reqdist, 0, 0);
            if (lv.getLastsisign() != null) {
                Location actualSiRefPos = getSignActualRefPos(lv.getLastsisign(), lv.getSavedworld());
                Location cartactualpos = getDriverseatActualPos(lv);
                notindist = (distFormula(actualSiRefPos, cartactualpos)) > 5;
            }
            // Wait doors fully closed then depart (if have red light in 5 meters do not depart)
            if (lv.getDooropen() == 0 && lv.isDoorconfirm() && lv.getBrake() != 9 && (lv.getLastsisp() != 0 || notindist) && lv.isAtoautodep() && lv.getAtsforced() == 0) {
                lv.setBrake(0);
                lv.setMascon(5);
            } else if (lv.isAtoautodep()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> waitDepart(lv), tickdelay);
                if (lv.getBrake() != 9) {
                    lv.setMascon(0);
                    lv.setBrake(8);
                }
            }
        }
    }
}