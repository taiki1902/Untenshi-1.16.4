package me.fiveave.untenshi;

import org.bukkit.entity.Player;

import static me.fiveave.untenshi.events.toEB;
import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.motion.*;

class ato {

    static void atosys(Player p, double accel, double decel, double ebdecel, double speeddrop, int[] speedsteps) {
        if (atodest.containsKey(p) && atospeed.containsKey(p) && !atsbraking.get(p) && !atsping.get(p) && atsforced.get(p).equals(0) && allowatousage.get(p)) {
            // Get distances (distnow: smaller value of atodist and signaldist)
            // reqatodist decelfr must be higher than others to prevent ATS-P or ATC run
            // suitableaccel is for N to accel when difference is at least 5, or already accelerating
            double reqatodist = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), 7, speedsteps), atospeed.get(p));
            double reqsidist;
            double reqspdist;
            double signaldist;
            double signaldistdiff;
            double speeddist;
            double speeddistdiff;
            double atodist = distFormula(atodest.get(p)[0] + 0.5, p.getLocation().getX(), atodest.get(p)[2] + 0.5, p.getLocation().getZ());
            double atodistdiff = atodist - reqatodist;
            double lowerSpeed = atospeed.get(p);
            double distnow = atodist;
            int currentlimit = Math.min(speedlimit.get(p), signallimit.get(p));
            int finalmascon = 0;
            boolean suitableaccel = (currentlimit - speed.get(p) > 5 && mascon.get(p) == 0) || mascon.get(p) > 0;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (distnow - reqdist is the smallest value)
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
                reqsidist = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), 6, speedsteps), lastsisp.get(p));
                signaldist = distFormula(lastsisign.get(p).getX() + 0.5, p.getLocation().getX(), lastsisign.get(p).getZ() + 0.5, p.getLocation().getZ());
                signaldistdiff = signaldist - reqsidist;
            } else {
                signaldist = Double.MAX_VALUE;
                signaldistdiff = Double.MAX_VALUE;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p)) {
                reqspdist = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), 6, speedsteps), lastspsp.get(p));
                speeddist = distFormula(lastspsign.get(p).getX() + 0.5, p.getLocation().getX(), lastspsign.get(p).getZ() + 0.5, p.getLocation().getZ());
                speeddistdiff = speeddist - reqspdist;
            } else {
                speeddist = Double.MAX_VALUE;
                speeddistdiff = Double.MAX_VALUE;
            }
            double priority = (atodistdiff < signaldistdiff) ? (Math.min(atodistdiff, speeddistdiff)) : (Math.min(signaldistdiff, speeddistdiff));
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p) && priority == signaldistdiff) {
                lowerSpeed = lastsisp.get(p);
                distnow = signaldist;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p) && priority == speeddistdiff) {
                lowerSpeed = lastspsp.get(p);
                distnow = speeddist;
            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            reqdist[9] = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), ebdecel, speedsteps), lowerSpeed);
            // Get speed drop distance
            reqdist[0] = getreqdist(p, speeddrop, lowerSpeed);
            for (int a = 1; a <= 8; a++) {
                // Minus speeddrop * 2 to make braking softer when reach 0 km/h
                reqdist[a] = getreqdist(p, ticksin1s * globaldecel(decel - speeddrop * 2 * (1 - speed.get(p) / speedsteps[5]), speed.get(p), a + 1, speedsteps), lowerSpeed);
            }
            // If no signal give it one
            lastsisp.putIfAbsent(p, 360);
            // Actual controlling part (midpt is arbitrary)
            double midpt = speed.get(p) * 1.8 / 3.6;
            atopisdirect.putIfAbsent(p, false);
            // Direct pattern?
            if (atopisdirect.get(p)) {
                if (speed.get(p) > lowerSpeed) {
                    double tempdist = lastsisp.get(p).equals(0) ? (distnow < 0 ? 0 : distnow - 5) : distnow;
                    for (int b = 9; b >= 0; b--) {
                        if (tempdist >= reqdist[b]) {
                            finalmascon = -b;
                        }
                        // If even emergency brake cannot brake in time
                        else if (tempdist < reqdist[9]) {
                            finalmascon = -9;
                        }
                    }
                } else if (suitableaccel) {
                    finalmascon = 5;
                }
            } else {
                // distnow - reqdist[5] > midpt: accel end not yet reached (no need to prepare for braking yet)
                if (distnow - reqdist[5] > midpt && suitableaccel && (!lastsisign.containsKey(p) || lastsisign.get(p).distance(p.getLocation()) > 5)) {
                    finalmascon = 5;
                }
                for (int b = 9; b >= 0; b--) {
                    // For braking not to 0 km/h
                    if (lowerSpeed > 0) {
                        // If cannot coast and distance is greater than braking distance (with default brake 5)
                        if (distnow >= reqdist[b] && distnow - reqdist[5] < midpt / 2) {
                            finalmascon = -b;
                        }
                        // If even emergency brake cannot brake in time
                        else if (distnow < reqdist[9]) {
                            finalmascon = -9;
                        }
                    } // For braking to 0 km/h
                    else {
                        // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
                        double tempdist = lastsisp.get(p).equals(0) ? (distnow < 0 ? 0 : distnow - 5) : distnow;
                        // If cannot coast and distance is greater than braking distance (with default brake 7) (midpt / 2 is arbitrary value)
                        if (tempdist >= reqdist[b] && tempdist - reqdist[7] < midpt / 2) {
                            finalmascon = -b;
                        }
                    }
                }
            }
            // Speeding prevention for all (accelswitch * 2 to prevent accel then decel)
            if (speed.get(p) + accelswitch(accel, 5, speed.get(p), speedsteps) * ticksin1s > currentlimit && finalmascon > 0) {
                finalmascon = 0;
            }
            // Slightly speeding auto braking (not for ATS-P or ATC)
            if (speed.get(p) > currentlimit) {
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
}