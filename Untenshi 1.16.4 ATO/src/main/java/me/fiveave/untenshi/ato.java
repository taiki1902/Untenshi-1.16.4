package me.fiveave.untenshi;

import org.bukkit.entity.Player;

import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.motion.gendefm;
import static me.fiveave.untenshi.motion.getreqdist;

public class ato {

    protected static void atosys(Player p, double decel, double ebdecel, double speeddrop, int i1, int i2, int i3, int i4) {
        if (atodest.containsKey(p) && atospeed.containsKey(p) && !atsebing.get(p) && !atsping.get(p) && atsforced.get(p).equals(0) && allowatousage.get(p)) {
            // Get distances (dist: smaller value of atodist and signaldist)
            // rqatodist decelfr must be higher than others to prevent ATS-P or ATC run
            double reqatodist = getreqdist(p, 10 * gendefm(decel, speed.get(p), 7, i1, i2, i3, i4), atospeed.get(p));
            double reqsidist;
            double reqspdist;
            double signaldist;
            double signaldistdiff;
            double speeddist;
            double speeddistdiff;
            double atodist = (Math.sqrt(Math.pow(atodest.get(p)[0] + 0.5 - p.getLocation().getX(), 2) + Math.pow(atodest.get(p)[2] + 0.5 - p.getLocation().getZ(), 2)));
            double atodistdiff = atodist - reqatodist;
            double lowerSpeed = atospeed.get(p);
            double dist = atodist;
            // Find either ATO, signal or speed limit distance, figure out which has the greatest priority (dist - reqdist is the smallest value)
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
                reqsidist = getreqdist(p, 10 * gendefm(decel, speed.get(p), 6, i1, i2, i3, i4), lastsisp.get(p));
                signaldist = (Math.sqrt(Math.pow(lastsisign.get(p).getX() + 0.5 - p.getLocation().getX(), 2) + Math.pow(lastsisign.get(p).getZ() + 0.5 - p.getLocation().getZ(), 2)));
                signaldistdiff = signaldist - reqsidist;
            } else {
                signaldist = Double.MAX_VALUE;
                signaldistdiff = Double.MAX_VALUE;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p)) {
                reqspdist = getreqdist(p, 10 * gendefm(decel, speed.get(p), 6, i1, i2, i3, i4), lastspsp.get(p));
                speeddist = (Math.sqrt(Math.pow(lastspsign.get(p).getX() + 0.5 - p.getLocation().getX(), 2) + Math.pow(lastspsign.get(p).getZ() + 0.5 - p.getLocation().getZ(), 2)));
                speeddistdiff = speeddist - reqspdist;
            } else {
                speeddist = Double.MAX_VALUE;
                speeddistdiff = Double.MAX_VALUE;
            }
            double priority = (atodistdiff < signaldistdiff) ? (Math.min(atodistdiff, speeddistdiff)) : (Math.min(signaldistdiff, speeddistdiff));
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p) && priority == signaldistdiff) {
                lowerSpeed = lastsisp.get(p);
                dist = signaldist;
            }
            if (lastspsign.containsKey(p) && lastspsp.containsKey(p) && priority == speeddistdiff) {
                lowerSpeed = lastspsp.get(p);
                dist = speeddist;

            }
            // Get brake distance (reqdist)
            double[] reqdist = new double[10];
            reqdist[9] = getreqdist(p, 10 * gendefm(decel, speed.get(p), ebdecel, i1, i2, i3, i4), lowerSpeed);
            // Get speed drop distance
            reqdist[0] = getreqdist(p, speeddrop, lowerSpeed);
            for (int a = 1; a <= 8; a++) {
                reqdist[a] = getreqdist(p, 10 * gendefm(decel, speed.get(p), a + 1, i1, i2, i3, i4), lowerSpeed);
            }
            // If no signal give it one
            lastsisp.putIfAbsent(p, 360);
            // Actual controlling part
            double midpt = speed.get(p) / 2;
            double midpt2 = speed.get(p) / 4;
            atopforcedirect.putIfAbsent(p, false);
            // Direct pattern?
            if (atopforcedirect.get(p)) {
                if (speed.get(p) > lowerSpeed) {
                    for (int b = 9; b >= 0; b--) {
                        if (dist >= reqdist[b]) {
                            mascon.put(p, -b);
                        }
                        // If even emergency brake cannot brake in time
                        else if (dist < reqdist[9]) {
                            mascon.put(p, -9);
                        }
                    }
                } else if (speed.get(p) + 5 < lowerSpeed) {
                    mascon.put(p, 5);
                } else if (speed.get(p) + 2 > lowerSpeed) {
                    mascon.put(p, 0);
                }
            } else {
                // dist - reqdist[5] > 1: prepare to brake
                if (dist - reqdist[5] > 1 && dist - reqdist[5] < midpt) {
                    mascon.put(p, 0);
                }
                // Accel end not yet reached (no need to prepare for braking yet)
                if (dist - reqdist[5] > midpt && ((speedlimit.get(p) - 5 < speed.get(p) && mascon.get(p) > 0) || speedlimit.get(p) - 5 >= speed.get(p)) && !overrun.get(p) && (!lastsisign.containsKey(p) || lastsisign.get(p).distance(p.getLocation()) > 5)) {
                    mascon.put(p, 5);
                }
                for (int b = 9; b >= 0; b--) {
                    // For braking not to 0 km/h
                    if (lowerSpeed > 0 && b > 0) {
                        if (dist < reqdist[1] && dist >= reqdist[b] && dist - reqdist[5] < midpt2) {
                            mascon.put(p, -b);
                        }
                        // If even emergency brake cannot brake in time
                        else if (dist < reqdist[9]) {
                            mascon.put(p, -9);
                        }
                    } // For braking to 0 km/h
                    else if (lowerSpeed == 0) {
                        // tempdist is for anti-ATS-run, stop at 5 m before 0 km/h signal
                        double tempdist = dist;
                        if (lastsisp.get(p).equals(0)) {
                            tempdist = tempdist - 5;
                            if (tempdist < 0) {
                                tempdist = 0;
                            }
                        }
                        // If cannot coast and distance is greater than braking distance (with default brake 7)
                        if (tempdist < reqdist[0] && tempdist >= reqdist[b] && tempdist - reqdist[7] < midpt2) {
                            mascon.put(p, -b);
                        }
                        // Zan-atsu-teisha in last 1 m
                        else if (tempdist < 1 && tempdist > reqdist[0]) {
                            mascon.put(p, 0);
                        }
                    }
                }
            }
            // Speeding prevention
            if ((speed.get(p) + 2 > speedlimit.get(p) || speed.get(p) + 2 > signallimit.get(p)) && mascon.get(p) > 0) {
                mascon.put(p, 0);
            }
            // Slightly speeding auto braking (not for ATS-P or ATC)
            if (speed.get(p) > speedlimit.get(p) || speed.get(p) > signallimit.get(p)) {
                int dummy = 8;
                for (int a = 8; a >= 1; a--) {
                    reqdist[a] = getreqdist(p, 10 * gendefm(decel, speed.get(p), a + 1, i1, i2, i3, i4), Math.min(speedlimit.get(p), signallimit.get(p)));
                    // If braking distance is greater than distance in 1 s
                    if (reqdist[a] <= speed.get(p) / 3.6) {
                        dummy = a;
                    }
                }
                // If the brake is greater, then use the value
                if (mascon.get(p) > -dummy) {
                    mascon.put(p, -dummy);
                }
            }
            // EB when overrun
            if (overrun.get(p) && atodist > 2) {
                mascon.put(p, -9);
            }
        }
    }
}