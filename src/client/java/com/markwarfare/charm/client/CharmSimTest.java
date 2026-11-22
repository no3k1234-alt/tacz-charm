package com.markwarfare.charm.client;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/**
 * Minecraft-independent shake test, shared by CharmSelfTest and the offline CharmHarness.
 * Runs 5 start poses times 6 shake scenarios and measures penetration depth and flip-overs.
 */
public final class CharmSimTest {
    private CharmSimTest() {
    }

    // Penetration deeper than this (model px) counts as a failure.
    public static final double FAIL_DEPTH = 0.1;
    public static final double H = 1.0 / 240;
    public static final double G = 9.8 * 15.3;
    // Shake strength: view acceleration cap (20 m/s^2) times response factor (0.55) times px per meter (15.3).
    public static final double SHAKE = 20 * 0.55 * 15.3;

    public static final String[] SCENARIOS = {"가만히", "좌우", "앞뒤", "위아래", "툭", "돌기"};
    public static final double[] SECONDS = {1.5, 2.5, 2.5, 2.5, 1.5, 2.5};
    // Start poses as {axis x, y, z, degrees}. The chain is rotated this much around the ring over
    // 0.25 s before the scenario begins.
    public static final double[][] STARTS = {{0, 0, 0, 0}, {0, 0, 1, 45}, {0, 0, 1, -45}, {1, 0, 0, 45}, {1, 0, 0, -45}};

    public static Vector3d acc(int s, double t, double total) {
        Vector3d a = new Vector3d(0, -G, 0);
        if (t >= total - 1.0) {
            return a; // hold still for the last second to check the rest pose
        }
        double w = 2 * Math.PI * 2;
        switch (s) {
            case 1 -> a.x += SHAKE * Math.sin(w * t);
            case 2 -> a.z += SHAKE * Math.sin(w * t);
            case 3 -> a.y += SHAKE * Math.sin(w * 1.5 * t);
            case 4 -> {
                if (t < 0.08) {
                    a.x += SHAKE * 2;
                } else if (t < 0.16) {
                    a.x -= SHAKE * 2;
                }
            }
            default -> {
            }
        }
        return a;
    }

    public record Run(double worstDepth, boolean over, CharmChain chain) {
    }

    // One run with scenario s and start pose si. If trace is non-null, chain points are logged
    // every 0.1 s.
    public static Run run(CharmChain.Charm charm, CharmChain.Ring ring, List<CharmGeom.Obb> wall, int s, int si, List<String> trace) {
        double[] st = STARTS[si];
        CharmChain c = new CharmChain();
        c.set(charm, ring, wall);
        c.reset();
        if (trace != null) {
            trace.add("매닮: " + c.dump());
        }
        if (st[3] != 0) {
            int ramp = (int) Math.round(0.25 / H);
            Quaterniond inc = new Quaterniond().fromAxisAngleDeg(st[0], st[1], st[2], st[3] / ramp);
            for (int n = 0; n < ramp; n++) {
                c.turn(inc);
                c.step(new Vector3d(0, -G, 0), H);
            }
        }
        double total = SECONDS[s];
        int steps = (int) Math.round(total / H);
        double worst = 0;
        for (int n = 0; n < steps; n++) {
            double t = n * H;
            if (s == 5 && t < total - 1.0) {
                // Turning the view left and right at 2 rad/s. The chain follows with an inertia factor of 0.65.
                c.turn(new Quaterniond().rotateY(2 * 0.65 * H * (t < (total - 1.0) / 2 ? 1 : -1)));
            }
            c.step(acc(s, t, total), H);
            if (t > 0.2) {
                double p = c.penetration();
                if (p > worst && trace != null && p > FAIL_DEPTH) {
                    trace.add(String.format("새 최대 t %.3f 뚫림 %.3f %s 닿음:%s", t, p, c.dump(), c.touching()));
                }
                worst = Math.max(worst, p);
            }
            if (trace != null && n % 24 == 0) {
                trace.add(String.format("t %.2f 뚫림 %.3f %s 닿음:%s", t, c.penetration(), c.dump(), c.touching()));
            }
        }
        // Flip-over: after resting, the chain end hangs less than half the chain length below the
        // attach point, meaning it is resting on top of something.
        double chain = 0;
        for (int i = 0; i < charm.segments(); i++) {
            chain += charm.rest[i].length();
        }
        boolean over = c.attach().y - c.point(c.count()).y < chain * 0.5;
        return new Run(worst, over, c);
    }
}
