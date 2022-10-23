package me.fiveave.untenshi;

import org.bukkit.entity.Player;

import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.motion.*;

class ato {

    static void atosys(Player p, double accel, double decel, double ebdecel, double speeddrop, int dcurrent, int[] speedsteps) {
        if (atodest.containsKey(p) && atospeed.containsKey(p) && !atsbraking.get(p) && !atsping.get(p) && atsforced.get(p).equals(0) && allowatousage.get(p)) {
            // Get distances (distnow: smaller value of atodist and signaldist)
            // rqatodist decelfr must be higher than others to prevent ATS-P or ATC run
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
            double accellimit = accelswitch(accel, dcurrent, speed.get(p), speedsteps) * ticksin1s;
            int currentlimit = Math.min(speedlimit.get(p), signallimit.get(p));
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
                reqdist[a] = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), a + 1, speedsteps), lowerSpeed);
            }
            // If no signal give it one
            lastsisp.putIfAbsent(p, 360);
            // Actual controlling part
            double midpt = speed.get(p) * 2.52 / 3.6;
            atopisdirect.putIfAbsent(p, false);
            // Direct pattern?
            if (atopisdirect.get(p)) {
                if (speed.get(p) > lowerSpeed) {
                    for (int b = 9; b >= 0; b--) {
                        if (distnow >= reqdist[b]) {
                            mascon.put(p, -b);
                        }
                        // If even emergency brake cannot brake in time
                        else if (distnow < reqdist[9]) {
                            mascon.put(p, -9);
                        }
                    }
                } else if (speed.get(p) + accellimit < lowerSpeed) {
                    mascon.put(p, 5);
                }
            } else {
                // distnow - reqdist[5] > 1: prepare to brake
                if (distnow - reqdist[5] > 1 && distnow - reqdist[5] < midpt) {
                    mascon.put(p, 0);
                }
                // Accel end not yet reached (no need to prepare for braking yet)
                if (distnow - reqdist[5] > midpt && currentlimit - speed.get(p) > 5 && !overrun.get(p) && (!lastsisign.containsKey(p) || lastsisign.get(p).distance(p.getLocation()) > 5)) {
                    mascon.put(p, 5);
                }
                for (int b = 9; b >= 0; b--) {
                    // For braking not to 0 km/h
                    if (lowerSpeed > 0) {
                        // If cannot coast and distance is greater than braking distance (with default brake 5)
                        if (distnow >= reqdist[b] && distnow - reqdist[5] < midpt / 2) {
                            mascon.put(p, -b);
                        }
                        // If even emergency brake cannot brake in time
                        else if (distnow < reqdist[9]) {
                            mascon.put(p, -9);
                        }
                    } // For braking to 0 km/h
                    else {
                        // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
                        double tempdist = lastsisp.get(p).equals(0) ? (distnow < 0 ? 0 : distnow - 5) : distnow;
                        // If cannot coast and distance is greater than braking distance (with default brake 7) (midpt / 2 is arbitrary value)
                        if (tempdist >= reqdist[b] && tempdist - reqdist[7] < midpt / 2) {
                            mascon.put(p, -b);
                        }
                    }
                }
            }
            // Speeding prevention for all
            if (speed.get(p) + accellimit > currentlimit && mascon.get(p) > 0) {
                mascon.put(p, 0);
            }
            // Slightly speeding auto braking (not for ATS-P or ATC)
            if (speed.get(p) > currentlimit) {
                for (int a = 8; a >= 1; a--) {
                    reqdist[a] = getreqdist(p, ticksin1s * globaldecel(decel, speed.get(p), a + 1, speedsteps), currentlimit);
                    // If braking distance is greater than distance in 1 s and if the brake is greater, then use the value
                    if (reqdist[a] <= speed.get(p) / 3.6 && mascon.get(p) > -a) {
                        mascon.put(p, -a);
                    }
                }
            }
            // EB when overrun
            if (overrun.get(p) && atodist > 2) {
                mascon.put(p, -9);
            }
        }
    }
}