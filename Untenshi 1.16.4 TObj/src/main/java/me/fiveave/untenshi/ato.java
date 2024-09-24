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
import static me.fiveave.untenshi.speedsign.getSignToRailOffset;

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
            Location headLoc = mg.head().getEntity().getLocation();
            Location tailLoc = mg.tail().getEntity().getLocation();
            Location atoLocForSlope = new Location(mg.getWorld(), lv.getAtodest()[0] + 0.5, lv.getAtodest()[1] + cartyposdiff, lv.getAtodest()[2] + 0.5);
            double slopeaccelnow = getSlopeAccel(headLoc, tailLoc);
            double slopeaccelsel = getSlopeAccel(atoLocForSlope, tailLoc);
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double reqatodist = getSingleReqdist(lv, lv.getSpeed(), lv.getAtospeed(), speeddrop, 6, slopeaccelsel, true, 0) + getThinkingDistance(lv, lv.getSpeed(), lv.getAtospeed(), decel, 6, slopeaccelsel, 0);
            double signaldist = Double.MAX_VALUE;
            double signaldistdiff = Double.MAX_VALUE;
            double speeddist = Double.MAX_VALUE;
            double speeddistdiff = Double.MAX_VALUE;
            double atodist = distFormula(lv.getAtodest()[0] + 0.5, headLoc.getX(), lv.getAtodest()[2] + 0.5, headLoc.getZ());
            double atodistdiff = atodist - reqatodist;
            double reqsidist;
            double reqspdist;
            double distnow = atodist;
            int currentlimit = minSpeedLimit(lv);
            int finalmascon = 0;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (lv.getLastsisign() != null && lv.getLastsisp() != maxspeed) {
                int[] getSiOffset = getSignToRailOffset(lv.getLastsisign(), mg.getWorld());
                Location siLocForSlope = new Location(mg.getWorld(), lv.getLastsisign().getX() + getSiOffset[0], lv.getLastsisign().getY() + getSiOffset[1] + cartyposdiff, lv.getLastsisign().getZ() + getSiOffset[2]);
                slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
                reqsidist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastsisp(), speeddrop, 6, slopeaccelsi, true, 0) + getThinkingDistance(lv, lv.getSpeed(), lv.getLastsisp(), decel, 6, slopeaccelsi, 0);
                signaldist = distFormula(lv.getLastsisign().getX() + getSiOffset[0] + 0.5, headLoc.getX(), lv.getLastsisign().getZ() + getSiOffset[2] + 0.5, headLoc.getZ());
                signaldistdiff = signaldist - reqsidist;
            }
            if (lv.getLastspsign() != null && lv.getLastspsp() != maxspeed) {
                int[] getSpOffset = getSignToRailOffset(lv.getLastspsign(), mg.getWorld());
                Location spLocForSlope = new Location(mg.getWorld(), lv.getLastspsign().getX() + getSpOffset[0], lv.getLastspsign().getY() + getSpOffset[1] + cartyposdiff, lv.getLastspsign().getZ() + getSpOffset[2]);
                slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
                reqspdist = getSingleReqdist(lv, lv.getSpeed(), lv.getLastspsp(), speeddrop, 6, slopeaccelsp, true, 0) + getThinkingDistance(lv, lv.getSpeed(), lv.getLastspsp(), decel, 6, slopeaccelsp, 0);
                speeddist = distFormula(lv.getLastspsign().getX() + getSpOffset[0] + 0.5, headLoc.getX(), lv.getLastspsign().getZ() + getSpOffset[2] + 0.5, headLoc.getZ());
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
            boolean allowaccel = ((currentlimit - lv.getSpeed() > 5 && (lowerSpeed - lv.getSpeed() > 5 || tempdist > speed1s(lv)) && lv.getMascon() == 0) || lv.getMascon() > 0) && potentialspeed <= currentlimit && (potentialspeed <= lowerSpeed || tempdist > speed1s(lv)) && !lv.isOverrun() && (lv.getDooropen() == 0 && lv.isDoorconfirm());
            // Actual controlling part
            getAllReqdist(lv, lv.getSpeed(), lowerSpeed, speeddrop, reqdist, slopeaccelsel, true, 1.0 / ticksin1s);
            // Require accel? (no need to prepare for braking for next object and ATO target destination + additional thinking distance)
            boolean notnearreqdist = tempdist > reqdist[6] + getThinkingDistance(lv, potentialspeed, lowerSpeed, decel, 6, slopeaccelsel, 3);
            if (notnearreqdist && allowaccel) {
                finalmascon = 5;
            }
            // Require braking? (with additional thinking time, slope acceleration considered)
            if (tempdist < reqdist[6] + (lv.getSpeed() + slopeaccelsel) / 3.6 / ticksin1s * 5) {
                lv.setAtoforcebrake(true);
            }
            // Direct pattern or forced?
            if (lv.isAtoforcebrake() || lv.isAtopisdirect()) {
                // If even emergency brake cannot brake in time
                finalmascon = -9;
                for (int b = 9; b >= 0; b--) {
                    if (tempdist >= reqdist[b]) {
                        finalmascon = -b;
                    }
                }
            }
            // Cancel braking? (Slope acceleration considered)
            if (tempdist > reqdist[6] + getThinkingDistance(lv, lv.getSpeed() + slopeaccelsel, lowerSpeed, decel, 6, slopeaccelsel, 3)) {
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
                getAllReqdist(lv, lv.getSpeed() + slopeaccelnow, currentlimit, speeddrop, reqdist, slopeaccelnow, true, 0);
                int finalbrake = -8;
                for (int a = 8; a >= 1; a--) {
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (speed1s(lv) >= reqdist[a]) {
                        finalbrake = -a;
                    }
                }
                if (finalmascon > finalbrake) {
                    finalmascon = finalbrake;
                }
            }
            // Final value
            lv.setMascon(finalmascon);
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
            lv.setMascon(-8);
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
            getAllReqdist(lv, minSpeedLimit(lv), 0, lv.getSpeeddrop(), reqdist, 0, true, 0);
            if (lv.getLastsisign() != null) {
                notindist = (distFormula(lv.getLastsisign().getX(), lv.getDriverseat().getEntity().getLocation().getX(), lv.getLastsisign().getZ(), lv.getDriverseat().getEntity().getLocation().getZ())) > 5;
            }
            // Wait doors fully closed then depart (if have red light in 5 meters do not depart)
            if (lv.getDooropen() == 0 && lv.isDoorconfirm() && lv.getMascon() != -9 && (lv.getLastsisp() != 0 || notindist) && lv.isAtoautodep() && lv.getAtsforced() == 0) {
                lv.setMascon(5);
            } else if (lv.isAtoautodep()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> waitDepart(lv), tickdelay);
                if (lv.getMascon() != -9) {
                    lv.setMascon(-8);
                }
            }
        }
    }
}