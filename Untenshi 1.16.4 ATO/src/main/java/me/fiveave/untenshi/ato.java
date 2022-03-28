package me.fiveave.untenshi;

import org.bukkit.entity.Player;

import static me.fiveave.untenshi.main.*;
import static me.fiveave.untenshi.main.mascon;
import static me.fiveave.untenshi.motion.gendefm;
import static me.fiveave.untenshi.motion.getreqdist;

public class ato {

    protected static void atosys(Player p, double decel, double ebdecel, double speeddrop, int i1, int i2, int i3, int i4) {
        if (atodest.containsKey(p) && atospeed.containsKey(p) && !atsebing.get(p) && atsforced.get(p).equals(0) && allowatousage.get(p)) {
            // Get distances (dist: smaller value of atodist and signaldist)
            double atodist = (Math.sqrt(Math.pow(atodest.get(p)[0] + 0.5 - p.getLocation().getX(), 2) + Math.pow(atodest.get(p)[2] + 0.5 - p.getLocation().getZ(), 2)));
            double signaldist;
            double dist;
            double lowerSpeed;
            // Have signal limit?
            if (lastsisign.containsKey(p) && lastsisp.containsKey(p)) {
                double reqatodist = getreqdist(p, 10 * gendefm(decel, speed.get(p), 6, i1, i2, i3, i4), atospeed.get(p));
                signaldist = (Math.sqrt(Math.pow(lastsisign.get(p).getX() + 0.5 - p.getLocation().getX(), 2) + Math.pow(lastsisign.get(p).getZ() + 0.5 - p.getLocation().getZ(), 2)));
                // Compare distances and set lower one
                // If atodist < signaldist only need to consider atodist
                if (atodist < signaldist) {
                    lowerSpeed = Math.min(signallimit.get(p), atospeed.get(p));
                    dist = atodist;
                } else {
                    // But if in reverse need to consider if it needs to brake (e.g. next station)
                    if (atodist - reqatodist < speed.get(p) / 2) {
                        lowerSpeed = Math.min(signallimit.get(p), atospeed.get(p));
                        dist = atodist;
                    } else {
                        lowerSpeed = Math.min(signallimit.get(p), lastsisp.get(p));
                        dist = signaldist;
                    }
                }
            } else {
                // If no detected last sign
                dist = atodist;
                lowerSpeed = Math.min(signallimit.get(p), atospeed.get(p));
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
                // For braking not to 0 km/h
                for (int b = 9; b >= 1; b--) {
                    if (lowerSpeed > 0 && dist < reqdist[1] && dist >= reqdist[b] && dist - reqdist[5] < midpt2) {
                        mascon.put(p, -b);
                    }
                    // If even emergency brake cannot brake in time
                    else if (dist < reqdist[9]) {
                        mascon.put(p, -9);
                    }
                }
                // For braking to 0 km/h
                for (int b = 9; b >= 0; b--) {
                    if (lowerSpeed == 0) {
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
            // Speeding auto braking
            if (speed.get(p) > speedlimit.get(p) || speed.get(p) > signallimit.get(p)) {
                int dummy = 8;
                for (int a = 8; a >= 1; a--) {
                    reqdist[a] = getreqdist(p, 10 * gendefm(decel, speed.get(p), a + 1, i1, i2, i3, i4), speedlimit.get(p));
                    if (reqdist[a] <= speed.get(p) / 3.6) {
                        dummy = a;
                    }
                }
                if (mascon.get(p) > -dummy) {
                    mascon.put(p, -dummy);
                }
            }
            // EB when speeding or overrun
            if ((speed.get(p) > speedlimit.get(p) + 3 || speed.get(p) > signallimit.get(p) + 3) || overrun.get(p) && atodist > 2) {
                mascon.put(p, -9);
            }
        }
    }
}