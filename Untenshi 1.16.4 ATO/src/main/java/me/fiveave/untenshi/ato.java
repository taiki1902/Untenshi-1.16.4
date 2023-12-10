package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import static me.fiveave.untenshi.events.doorControls;
import static me.fiveave.untenshi.events.toEB;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.motion.*;
import static me.fiveave.untenshi.speedsign.getSignToRailOffset;

class ato {

    static void atosys(untenshi ld, double accel, double decel, double ebdecel, double speeddrop, int[] speedsteps, MinecartGroup mg) {
        if (ld.getAtodest() != null && ld.getAtospeed() != -1 && !ld.isAtsping() && ld.getAtsforced() == 0 && ld.isAllowatousage()) {
            /*
             Get distances (distnow: smaller value of atodist and signaldist)
             reqatodist rate must be higher than others to prevent ATS-P or ATC run
             allowaccel is for N to accel when difference is at least 5, or already accelerating
            */
            double lowerSpeed = ld.getAtospeed();
            // 0.0625 from result of getting mg.head() y-location
            Location headLoc = mg.head().getEntity().getLocation();
            Location tailLoc = mg.tail().getEntity().getLocation();
            Location atoLocForSlope = new Location(mg.getWorld(), ld.getAtodest()[0] + 0.5, ld.getAtodest()[1] + cartYPosDiff, ld.getAtodest()[2] + 0.5);
            double slopeaccelnow = getSlopeAccel(headLoc, tailLoc);
            double slopeaccelsel = getSlopeAccel(atoLocForSlope, tailLoc);
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double reqatodist = getReqdist(ld, avgRangeDecel(decel, ld.getSpeed(), ld.getAtospeed(), 7, speedsteps), ld.getAtospeed(), slopeaccelsel, speeddrop);
            double signaldist = Double.MAX_VALUE;
            double signaldistdiff = Double.MAX_VALUE;
            double speeddist = Double.MAX_VALUE;
            double speeddistdiff = Double.MAX_VALUE;
            double atodist = distFormula(ld.getAtodest()[0] + 0.5, headLoc.getX(), ld.getAtodest()[2] + 0.5, headLoc.getZ());
            double atodistdiff = atodist - reqatodist;
            double reqsidist;
            double reqspdist;
            double distnow = atodist;
            int currentlimit = minSpeedLimit(ld);
            int finalmascon = 0;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (ld.getLastsisign() != null && ld.getLastsisp() != maxspeed) {
                int[] getSiOffset = getSignToRailOffset(ld.getLastsisign(), mg.getWorld());
                Location siLocForSlope = new Location(mg.getWorld(), ld.getLastsisign().getX() + getSiOffset[0], ld.getLastsisign().getY() + getSiOffset[1] + cartYPosDiff, ld.getLastsisign().getZ() + getSiOffset[2]);
                slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
                reqsidist = getReqdist(ld, avgRangeDecel(decel, ld.getSpeed(), ld.getLastsisp(), 6, speedsteps), ld.getLastsisp(), slopeaccelsi, speeddrop);
                signaldist = distFormula(ld.getLastsisign().getX() + getSiOffset[0] + 0.5, headLoc.getX(), ld.getLastsisign().getZ() + getSiOffset[2] + 0.5, headLoc.getZ());
                signaldistdiff = signaldist - reqsidist;
            }
            if (ld.getLastspsign() != null && ld.getLastspsp() != maxspeed) {
                int[] getSpOffset = getSignToRailOffset(ld.getLastspsign(), mg.getWorld());
                Location spLocForSlope = new Location(mg.getWorld(), ld.getLastspsign().getX() + getSpOffset[0], ld.getLastspsign().getY() + getSpOffset[1] + cartYPosDiff, ld.getLastspsign().getZ() + getSpOffset[2]);
                slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
                reqspdist = getReqdist(ld, avgRangeDecel(decel, ld.getSpeed(), ld.getLastspsp(), 6, speedsteps), ld.getLastspsp(), slopeaccelsp, speeddrop);
                speeddist = distFormula(ld.getLastspsign().getX() + getSpOffset[0] + 0.5, headLoc.getX(), ld.getLastspsign().getZ() + getSpOffset[2] + 0.5, headLoc.getZ());
                speeddistdiff = speeddist - reqspdist;
            }
            double priority = (atodistdiff < signaldistdiff) ? (Math.min(atodistdiff, speeddistdiff)) : (Math.min(signaldistdiff, speeddistdiff));
            if (ld.getLastsisign() != null && ld.getLastsisp() != maxspeed && priority == signaldistdiff) {
                lowerSpeed = ld.getLastsisp();
                distnow = signaldist;
                slopeaccelsel = slopeaccelsi;
            }
            if (ld.getLastspsign() != null && ld.getLastspsp() != maxspeed && priority == speeddistdiff) {
                lowerSpeed = ld.getLastspsp();
                distnow = speeddist;
                slopeaccelsel = slopeaccelsp;
            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            getAllReqdist(ld, decel, ebdecel, speeddrop, speedsteps, lowerSpeed, reqdist, slopeaccelsel);
            // Potential acceleration (acceleration after P5 to N)
            double potentialaccel = accelSwitch(accel, 5, ld.getSpeed(), speedsteps) + slopeaccelsel;
            boolean allowaccel = ((currentlimit - ld.getSpeed() > 5 && ld.getMascon() == 0) || ld.getMascon() > 0) && ld.getSpeed() + potentialaccel / 2 <= currentlimit && !ld.isOverrun();
            // Actual controlling part
            // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
            boolean nextredlight = ld.getLastsisp() == 0 && priority == signaldistdiff;
            double tempdist = nextredlight ? (distnow - 5 < 0 ? 0 : distnow - 5) : distnow;
            // Require accel? (no need to prepare for braking yet + additional thinking time)
            if (tempdist - reqdist[6] > speed1s(ld) * (getThinkingTime(ld, 6) + 2) && allowaccel && !(nextredlight && tempdist < 10)) {
                finalmascon = 5;
            }
            // Require braking? (additional thinking time to prevent braking too hard)
            if (tempdist < reqdist[6] + speed1s(ld) * getThinkingTime(ld, 6)) {
                ld.setAtoforcebrake(true);
            }
            // Direct pattern or forced?
            if (ld.isAtoforcebrake() || ld.isAtopisdirect()) {
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
            if (tempdist > reqdist[1] + speed1s(ld) * getThinkingTime(ld, 1)) {
                ld.setAtoforcebrake(false);
            }
            // Red light waiting procedure
            if (nextredlight && ld.getSpeed() == 0) {
                waitDepart(ld);
            }
            // Slightly speeding auto braking (not related to ATS-P or ATC)
            if (ld.getSpeed() + slopeaccelnow > currentlimit) {
                // Redefine reqdist (here for braking distance to speed limit)
                getAllReqdist(ld, decel, ebdecel, speeddrop, speedsteps, currentlimit, reqdist, slopeaccelnow);
                int finalbrake = -8;
                for (int a = 8; a >= 1; a--) {
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (reqdist[a] <= ld.getSpeed() / 3.6) {
                        finalbrake = -a;
                    }
                }
                if (finalmascon > finalbrake) {
                    finalmascon = finalbrake;
                }
            }
            // Final value
            ld.setMascon(finalmascon);
            // EB when overrun
            if (ld.isOverrun() && atodist > 1) {
                toEB(ld);
            }
        }
    }

    static void getAllReqdist(untenshi ld, double decel, double ebdecel, double speeddrop, int[] speedsteps, double lowerSpeed, double[] reqdist, double slopeaccel) {
        // Consider normal case or else EB will be too common (decelfr = 7 because no multiplier)
        reqdist[9] = getReqdist(ld, avgRangeDecel(ebdecel, ld.getSpeed(), lowerSpeed, 7, speedsteps), lowerSpeed, slopeaccel, speeddrop);
        // Get speed drop distance
        reqdist[0] = getReqdist(ld, speeddrop, lowerSpeed, slopeaccel, speeddrop);
        for (int a = 1; a <= 8; a++) {
            // Plus reaction time + consider speed after adding slopeaccel to prevent reaction lag
            reqdist[a] = getReqdist(ld, avgRangeDecel(decel, ld.getSpeed(), lowerSpeed, a + 1, speedsteps), lowerSpeed, slopeaccel, speeddrop) + ld.getSpeed() / 3.6 * getThinkingTime(ld, a) / 2;
        }
    }

    static double getThinkingTime(untenshi ld, int a) {
        return Math.max(1.0 / ticksin1s, Math.min(a * 0.2, (a + (ld.getCurrent() * 9 / 480)) * 0.2));
    }

    static double speed1s(untenshi ld) {
        return ld.getSpeed() / 3.6;
    }

    // ATO Stop Time Countdown
    static void atoDepartCountdown(untenshi ld) {
        if (ld.isPlaying() && ld.getAtostoptime() != -1) {
            if (ld.getAtostoptime() > 0 && ld.isDoordiropen()) {
                ld.setAtostoptime(ld.getAtostoptime() - 1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> atoDepartCountdown(ld), 20);
            } else {
                doorControls(ld, false);
                // Reset values in order to depart
                ld.setAtostoptime(-1);
                ld.setAtodest(null);
                ld.setAtospeed(-1);
                waitDepart(ld);
            }
        }
    }

    private static void waitDepart(untenshi ld) {
        if (ld.isPlaying()) {
            // Wait doors fully closed then depart
            if (ld.getDooropen() == 0 && ld.isDoorconfirm() && ld.getMascon() != -9) {
                ld.setMascon(5);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> waitDepart(ld), tickdelay);
                if (ld.getMascon() != -9) {
                    ld.setMascon(-8);
                }
            }
        }
    }
}