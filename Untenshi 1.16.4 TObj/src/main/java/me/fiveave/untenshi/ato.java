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

    static void atosys(utsvehicle lv, MinecartGroup mg) {
        double decel = lv.getDecel();
        double speeddrop = lv.getSpeeddrop();
        int[] speedsteps = lv.getSpeedsteps();
        if (lv.getAtodest() != null && lv.getAtospeed() != -1 && lv.getAtsping() == 0 && lv.getAtsforced() == 0 && (lv.getLd() == null || lv.getLd().isAllowatousage())) {
            /*
             Get distances (distnow: smaller value of atodist and signaldist)
             reqatodist rate must be higher than others to prevent ATS-P or ATC run
             allowaccel is for N to accel when difference is at least 5, or already accelerating
            */
            double lowerSpeed = lv.getAtospeed();
            // 0.0625 from result of getting mg.head() y-location
            Location headLoc = mg.head().getEntity().getLocation();
            Location tailLoc = mg.tail().getEntity().getLocation();
            Location atoLocForSlope = new Location(mg.getWorld(), lv.getAtodest()[0] + 0.5, lv.getAtodest()[1] + cartYPosDiff, lv.getAtodest()[2] + 0.5);
            double slopeaccelnow = getSlopeAccel(headLoc, tailLoc);
            double slopeaccelsel = getSlopeAccel(atoLocForSlope, tailLoc);
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double reqatodist = getReqdist(lv.getSpeed(), lv.getAtospeed(), avgRangeDecel(decel, lv.getSpeed(), lv.getAtospeed(), 7, speedsteps), slopeaccelsel, speeddrop);
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
            getAllReqdist(lv, lv.getSpeed(), lowerSpeed, speeddrop, reqdist, slopeaccelsel);
            // Potential acceleration (acceleration after P5 to N) (0.75 from result of average accel of P1-P5 divided by P5 accel + delay)
            double sumallaccel = 0;
            for (int i = 1; i <= 5; i++) {
                sumallaccel += accelSwitch(lv, lv.getSpeed(), i);
            }
            double potentialaccel = sumallaccel / 5 + slopeaccelsel;
            boolean allowaccel = ((currentlimit - lv.getSpeed() > 5 && lv.getMascon() == 0) || lv.getMascon() > 0) && lv.getSpeed() + potentialaccel <= currentlimit && !lv.isOverrun() && (lowerSpeed > 0 || distnow > 1) && (lv.getDooropen() == 0 && lv.isDoorconfirm());
            // Actual controlling part
            // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
            boolean nextredlight = lv.getLastsisp() == 0 && priority == signaldistdiff;
            double tempdist = nextredlight ? (distnow - 5 < 0 ? 0 : distnow - 5) : distnow;
            // Require accel? (no need to prepare for braking yet + additional thinking time)
            if (tempdist - reqdist[6] > speed1s(lv) * (getThinkingTime(lv, 6) + 2) && allowaccel && !(nextredlight && tempdist < 10)) {
                finalmascon = 5;
            }
            // Require braking? (additional thinking time to prevent braking too hard)
            if (tempdist < reqdist[6] + speed1s(lv) * getThinkingTime(lv, 6)) {
                lv.setAtoforcebrake(true);
            }
            // Direct pattern or forced?
            if (lv.isAtoforcebrake() || lv.isAtopisdirect()) {
                // If even emergency brake cannot brake in time
                finalmascon = -9;
                // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
                for (int b = 9; b >= 0; b--) {
                    if (tempdist >= reqdist[b]) {
                        finalmascon = -b;
                    }
                }
            }
            // Cancel braking?
            if (tempdist > reqdist[1] + speed1s(lv) * getThinkingTime(lv, 1)) {
                lv.setAtoforcebrake(false);
            }
            // Red light waiting procedure
            if (nextredlight && lv.getSpeed() == 0) {
                waitDepart(lv);
            }
            // Slightly speeding auto braking (not related to ATS-P or ATC)
            if (lv.getSpeed() + slopeaccelnow > currentlimit) {
                // Redefine reqdist (here for braking distance to speed limit)
                getAllReqdist(lv, lv.getSpeed() + slopeaccelnow, currentlimit, speeddrop, reqdist, slopeaccelnow);
                int finalbrake = -8;
                for (int a = 8; a >= 1; a--) {
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (reqdist[a] <= lv.getSpeed() / 3.6) {
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
            generalMsg(lv.getLd().getP(), ChatColor.GOLD, getlang("ato_patterncancel"));
        }
    }

    static void getAllReqdist(utsvehicle lv, double upperSpeed, double lowerSpeed, double speeddrop, double[] reqdist, double slopeaccel) {
        double decel = lv.getDecel();
        double ebdecel = lv.getEbdecel();
        int[] speedsteps = lv.getSpeedsteps();
        // Consider normal case or else EB will be too common (decelfr = 7 because no multiplier)
        reqdist[9] = getReqdist(upperSpeed, lowerSpeed, avgRangeDecel(ebdecel, upperSpeed, lowerSpeed, 7, speedsteps), slopeaccel, speeddrop);
        // Get speed drop distance
        reqdist[0] = getReqdist(upperSpeed, lowerSpeed, speeddrop, slopeaccel, speeddrop);
        for (int a = 1; a <= 8; a++) {
            // Plus reaction time + consider speed after adding slopeaccel to prevent reaction lag
            reqdist[a] = getReqdist(upperSpeed, lowerSpeed, avgRangeDecel(decel, upperSpeed, lowerSpeed, a + 1, speedsteps), slopeaccel, speeddrop) + upperSpeed / 3.6 * getThinkingTime(lv, a) / 2;
        }
    }

    static double getThinkingTime(utsvehicle lv, int a) {
        return Math.max(1.0 / ticksin1s, Math.min(a * 0.2, (a + (lv.getCurrent() * 9 / 480)) * 0.2));
    }

    static double speed1s(utsvehicle lv) {
        return lv.getSpeed() / 3.6;
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
            getAllReqdist(lv, minSpeedLimit(lv), 0, lv.getSpeeddrop(), reqdist, 0);
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