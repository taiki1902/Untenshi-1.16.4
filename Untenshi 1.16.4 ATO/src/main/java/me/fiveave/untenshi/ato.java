package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import static me.fiveave.untenshi.events.toEB;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.motion.*;

class ato {

    static void atosys(Player p, double accel, double decel, double ebdecel, double speeddrop, int[] speedsteps, MinecartGroup mg) {
        if (atodest.containsKey(p) && atospeed.containsKey(p) && !atsbraking.get(p) && !atsping.get(p) && atsforced.get(p).equals(0) && allowatousage.get(p)) {
            /*
             Get distances (distnow: smaller value of atodist and signaldist)
             reqatodist decelfr must be higher than others to prevent ATS-P or ATC run
             allowaccel is for N to accel when difference is at least 5, or already accelerating
            */
            double lowerSpeed = atospeed.get(p);
            // 0.0625 from result of getting mg.head() y-location
            final double cartPosDiff = 0.0625;
            Location headLoc = mg.head().getEntity().getLocation();
            Location tailLoc = mg.tail().getEntity().getLocation();
            Location atoLocForSlope = new Location(mg.getWorld(), atodest.get(p)[0] + 0.5, atodest.get(p)[1] + cartPosDiff, atodest.get(p)[2] + 0.5);
            double slopeaccel = getSlopeAccel(atoLocForSlope, tailLoc);
            double slopeaccelsi = 0;
            double slopeaccelsp = 0;
            double reqatodist = getreqdist(p, globaldecel(decel, speed.get(p), 7, speedsteps), atospeed.get(p), slopeaccel, speeddrop);
            double signaldist = Double.MAX_VALUE;
            double signaldistdiff = Double.MAX_VALUE;
            double speeddist = Double.MAX_VALUE;
            double speeddistdiff = Double.MAX_VALUE;
            double atodist = distFormula(atodest.get(p)[0] + 0.5, p.getLocation().getX(), atodest.get(p)[2] + 0.5, p.getLocation().getZ());
            double atodistdiff = atodist - reqatodist;
            double reqsidist;
            double reqspdist;
            double distnow = atodist;
            int currentlimit = minSpeedLimit(p);
            int finalmascon = 0;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
                Location siLocForSlope = new Location(mg.getWorld(), lastsisign.get(p).getX(), lastsisign.get(p).getY() + cartPosDiff, lastsisign.get(p).getZ());
                slopeaccelsi = getSlopeAccel(siLocForSlope, tailLoc);
                reqsidist = getreqdist(p, globaldecel(decel, speed.get(p), 6, speedsteps), lastsisp.get(p), slopeaccelsi, speeddrop);
                signaldist = distFormula(lastsisign.get(p).getX() + 0.5, p.getLocation().getX(), lastsisign.get(p).getZ() + 0.5, p.getLocation().getZ());
                signaldistdiff = signaldist - reqsidist;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p)) {
                Location spLocForSlope = new Location(mg.getWorld(), lastspsign.get(p).getX(), lastspsign.get(p).getY() + cartPosDiff, lastspsign.get(p).getZ());
                slopeaccelsp = getSlopeAccel(spLocForSlope, tailLoc);
                reqspdist = getreqdist(p, globaldecel(decel, speed.get(p), 6, speedsteps), lastspsp.get(p), slopeaccelsp, speeddrop);
                speeddist = distFormula(lastspsign.get(p).getX() + 0.5, p.getLocation().getX(), lastspsign.get(p).getZ() + 0.5, p.getLocation().getZ());
                speeddistdiff = speeddist - reqspdist;
            }
            double priority = (atodistdiff < signaldistdiff) ? (Math.min(atodistdiff, speeddistdiff)) : (Math.min(signaldistdiff, speeddistdiff));
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
            getallreqdist(p, decel, ebdecel, speeddrop, speedsteps, lowerSpeed, reqdist, slopeaccel);
            double potentialaccel = accelswitch(accel, 5, speed.get(p), speedsteps) + slopeaccel;
            boolean allowaccel = ((currentlimit - speed.get(p) > 5 && mascon.get(p) == 0) || mascon.get(p) > 0) && speed.get(p) + potentialaccel / ticksin1s <= currentlimit && !overrun.get(p);
            // If no signal give it one
            lastsisp.putIfAbsent(p, 360);
            // Actual controlling part
            // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
            double tempdist = lastsisp.get(p).equals(0) ? (distnow < 0 ? 0 : distnow - 5) : distnow;
            // Require accel? (no need to prepare for braking yet + additional thinking time)
            if (tempdist - reqdist[6] > speed1s(p) * (getThinkingTime(p, 6) + 2) && allowaccel) {
                finalmascon = 5;
            }
            // Require braking? (additional thinking time to prevent braking too hard)
            if (tempdist < reqdist[6] + speed1s(p) * getThinkingTime(p, 6)) {
                atoforcebrake.put(p, true);
            }
            // Direct pattern or forced?
            if ((atoforcebrake.get(p) || atopisdirect.get(p))) {
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
            if (tempdist > reqdist[1] + speed1s(p) * (getThinkingTime(p, 1))) {
                atoforcebrake.put(p, false);
            }
            // Slightly speeding auto braking (not related to ATS-P or ATC)
            if (speed.get(p) > currentlimit) {
                // Redefine reqdist (here for braking distance to speed limit)
                getallreqdist(p, decel, ebdecel, speeddrop, speedsteps, currentlimit, reqdist, getSlopeAccel(headLoc, tailLoc));
                int finalbrake = -8;
                for (int a = 8; a >= 1; a--) {
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (reqdist[a] <= speed.get(p) / 3.6) {
                        finalbrake = -a;
                    }
                }
                if (finalmascon > finalbrake) {
                    finalmascon = finalbrake;
                }
            }
            // Final value
            mascon.put(p, finalmascon);
            // EB when overrun
            if (overrun.get(p) && atodist > 1) {
                toEB(p);
            }
        }
    }

    private static void getallreqdist(Player p, double decel, double ebdecel, double speeddrop, int[] speedsteps, double lowerSpeed, double[] reqdist, double slopeaccel) {
        double speedlater = speed.get(p) + slopeaccel;
        // Consider normal case or else EB will be too common
        reqdist[9] = getreqdist(p, globaldecel(decel, speed.get(p), ebdecel, speedsteps), lowerSpeed, slopeaccel, speeddrop);
        // Get speed drop distance
        reqdist[0] = getreqdist(p, speeddrop, lowerSpeed, slopeaccel, speeddrop);
        for (int a = 1; a <= 8; a++) {
            // Plus reaction time + consider speed after adding slopeaccel to prevent reaction lag
            reqdist[a] = getreqdist(p, globaldecel(decel, speedlater, a + 1, speedsteps), lowerSpeed, slopeaccel, speeddrop) + speedlater / 3.6 * getThinkingTime(p, a);
        }
    }

    private static double getThinkingTime(Player p, int a) {
        return Math.max(1.0 / ticksin1s, 1.0 / ticksin1s * Math.min(a, a + (current.get(p) * 9 / 480)));
    }

    static double speed1s(Player p) {
        return speed.get(p) / 3.6;
    }
}