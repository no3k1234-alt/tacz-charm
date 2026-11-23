package com.markwarfare.charm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * First-person charm sway driver. Feeds view rotation, eye acceleration and walk bob into the chain each frame.
 * All vectors here are in screen axes (right, up, back). Call frame() every frame, then read rotation(i).
 */
public final class CharmPhysics {
    // Fallback spring chain (guns without a charm mount). Each segment has a direction d and angular velocity w.
    // Segment 0 is pulled toward (gravity - anchor acceleration); segment i follows segment i-1, so it lags a beat.
    // Damping ratio 1 means it settles without overshoot.
    private static final double ROOT_HZ = 1.6;
    private static final double ROOT_DAMPING_RATIO = 0.32;
    private static final double LINK_HZ = 2.6;
    private static final double LINK_DAMPING_RATIO = 0.45;
    // Fraction of each frame's view rotation applied to the chain instantly (inertia left behind in the world).
    // Kept at 0: instant rotation made the chain snap sideways on fast flicks. View turns are fed in as a
    // smooth force instead (TURN_*).
    private static final double ROTATION_INERTIA = 0;
    // View turn -> force. TURN_ARM is the anchor offset from the eye (screen axes, m). TURN_SMOOTH filters the
    // angular velocity (s), TURN_DRAG_TAU is the velocity drag time (s), the gains weight the angular
    // acceleration and velocity terms, MAX_TURN caps the result (m/s²).
    private static final Vector3d TURN_ARM = new Vector3d(0.25, -0.2, -0.55);
    private static final double TURN_SMOOTH = 0.05;
    private static final double TURN_DRAG_TAU = 0.25;
    private static final double TURN_ACC_GAIN = 0.12;
    private static final double TURN_VEL_GAIN = 0.45;
    private static final double MAX_TURN = 8;
    private static final Vector3d turnVel = new Vector3d();
    private static final Vector3d lastTurnVel = new Vector3d();
    // How strongly anchor acceleration pushes the charm.
    private static final double ACCEL_GAIN = 0.55;
    // Low-pass time constant for anchor acceleration (s). Larger is smoother.
    private static final double ACCEL_SMOOTH = 0.06;
    private static final double MAX_ACCEL = 20;
    // Vertical walk bob acceleration (blocks/s²).
    private static final double WALK_BOUNCE = 6;
    // Soft limits (degrees). Past these the restoring force ramps up quickly instead of hitting a hard wall.
    private static final double SOFT_SWING_DEG = 55;
    private static final double SOFT_BEND_DEG = 30;
    // Max component toward the gun body (screen right). Beyond this the segment is pushed back out.
    private static final double MAX_INWARD = 0.2;
    private static final double GRAVITY = 9.8;
    // Extra gravity on the ornament, as a multiple of normal gravity (1.5 = 2.5x total).
    private static final double TAG_GRAVITY = 1.5;
    private static final int MAX_SEGMENTS = 8;

    // Guns with a charm mount use CharmChain, which collides against the actual model geometry.
    public static final CharmChain LIVE = new CharmChain();
    // Set every frame by AttachmentRenderMixin.
    private static boolean mount;
    // Screen acceleration (m/s²) to model px/s². Chosen so a 1.5 px chain swings at about 1.6 Hz.
    public static final double PX_ACCEL = 15.3;

    /** Sets the charm, ring and collision walls for a mounted gun. Pass nulls when the gun has no mount. */
    public static void setChain(CharmChain.Charm charm, CharmChain.Ring ring, java.util.List<CharmGeom.Obb> wall) {
        mount = charm != null && ring != null && wall != null;
        if (mount) {
            LIVE.set(charm, ring, wall);
        }
    }

    public static boolean hasMount() {
        return mount;
    }

    /** Attachment point in model axes (px), relative to the mount ring. */
    public static Vector3d attach() {
        return LIVE.attach();
    }

    public static String dump() {
        return LIVE.dump();
    }

    // Screen axes (right, up, back) to model axes. Model x points to screen left.
    private static Vector3d toModel(Vector3d v) {
        return new Vector3d(-v.x, v.y, v.z);
    }

    private static void mountReset() {
        LIVE.reset();
        LIVE.rotations(rotations);
    }

    private static int count = 1;
    private static Vector3d[] dir = {new Vector3d(0, -1, 0)};
    private static Vector3d[] omega = {new Vector3d()};
    private static Quaternionf[] rotations = {new Quaternionf()};
    private static double[] ratios = {1};

    private static Matrix3d lastBasis;
    private static Vec3 lastEye;
    private static Vec3 lastVel = Vec3.ZERO;
    private static final Vector3d smoothAcc = new Vector3d();
    private static long lastNanos;
    private static boolean valid;

    private CharmPhysics() {
    }


    public static Quaternionf rotation(int i) {
        return i >= 0 && i < rotations.length ? rotations[i] : rotations[0];
    }

    /** Sets segment length ratios from the model. State is rebuilt when the segment count changes. */
    public static void configure(double[] next) {
        if (next == null || next.length == 0) {
            next = new double[]{1};
        }
        int n = Math.min(next.length, MAX_SEGMENTS);
        if (n == count) {
            ratios = next;
            return;
        }
        count = n;
        ratios = next;
        dir = new Vector3d[n];
        omega = new Vector3d[n];
        rotations = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            dir[i] = new Vector3d(0, -1, 0);
            omega[i] = new Vector3d();
            rotations[i] = new Quaternionf();
        }
        valid = false;
    }

    public static void frame(Minecraft mc, float partialTick) {
        LocalPlayer player = mc.player;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        if (player == null) {
            valid = false;
            return;
        }
        if (mc.isPaused()) {
            return;
        }

        float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
        float pitch = player.getViewXRot(partialTick) * Mth.DEG_TO_RAD;
        Vector3d forward = new Vector3d(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vector3d right = new Vector3d(-forward.z, 0, forward.x);
        if (right.lengthSquared() < 1.0e-6) {
            right.set(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        }
        right.normalize();
        Vector3d up = new Vector3d(right).cross(forward).normalize();
        // Columns are the world directions of the screen axes (right, up, back), so basis * screen = world.
        Matrix3d basis = new Matrix3d(right, up, new Vector3d(forward).negate());
        Vec3 eye = player.getEyePosition(partialTick);

        if (!valid || dt <= 0 || dt > 0.25 || lastBasis == null || eye.distanceToSqr(lastEye) > 4) {
            for (int i = 0; i < count; i++) {
                dir[i].set(toLocal(basis, new Vector3d(0, -1, 0)));
                omega[i].zero();
            }
            smoothAcc.zero();
            turnVel.zero();
            lastTurnVel.zero();
            lastVel = Vec3.ZERO;
            lastEye = eye;
            lastBasis = basis;
            valid = true;
            if (mount) {
                mountReset();
            } else {
                updateRotations();
            }
            return;
        }

        // View rotation since last frame, expressed in the current screen frame. Only ROTATION_INERTIA of it
        // is applied directly.
        Matrix3d delta = new Matrix3d(basis).transpose().mul(lastBasis);
        Quaterniond turn = new Quaterniond().setFromNormalized(delta);
        Quaterniond partial = new Quaterniond().slerp(turn, ROTATION_INERTIA);
        for (int i = 0; i < count; i++) {
            partial.transform(dir[i]).normalize();
        }
        // Turn the filtered angular velocity into the inertial force felt at the anchor. The charm lags when a
        // turn starts and overshoots when it stops.
        org.joml.AxisAngle4d aa = new org.joml.AxisAngle4d().set(turn);
        if (aa.angle > Math.PI) {
            aa.angle -= 2 * Math.PI;
        }
        Vector3d wNow = Double.isFinite(aa.angle) && Math.abs(aa.angle) > 1.0e-9
                ? new Vector3d(aa.x, aa.y, aa.z).mul(aa.angle / dt) : new Vector3d();
        if (wNow.length() > 30) {
            wNow.normalize(30);
        }
        lastTurnVel.set(turnVel);
        turnVel.lerp(wNow, 1 - Math.exp(-dt / TURN_SMOOTH));
        Vector3d alpha = new Vector3d(turnVel).sub(lastTurnVel).div(dt);
        Vector3d turnAcc = new Vector3d(alpha).cross(TURN_ARM).mul(TURN_ACC_GAIN)
                .add(new Vector3d(turnVel).cross(TURN_ARM).mul(TURN_VEL_GAIN / TURN_DRAG_TAU));
        if (turnAcc.length() > MAX_TURN) {
            turnAcc.normalize(MAX_TURN);
        }

        // Anchor acceleration from the second difference of eye position, then low-passed.
        Vec3 vel = eye.subtract(lastEye).scale(1 / dt);
        Vec3 acc = vel.subtract(lastVel).scale(1 / dt);
        lastVel = vel;
        lastEye = eye;
        lastBasis = basis;
        Vector3d accLocal = toLocal(basis, new Vector3d(acc.x, acc.y, acc.z));
        if (accLocal.length() > MAX_ACCEL) {
            accLocal.normalize(MAX_ACCEL);
        }
        double a = 1 - Math.exp(-dt / ACCEL_SMOOTH);
        smoothAcc.lerp(accLocal, a);

        // One vertical bounce per step.
        double bob = Mth.lerp(partialTick, player.oBob, player.bob);
        double walk = player.walkDist + (player.walkDist - player.walkDistO) * partialTick;
        double bounce = Math.sin(walk * Math.PI * 2) * bob * 10 * WALK_BOUNCE;

        Vector3d gravity = toLocal(basis, new Vector3d(0, -GRAVITY, 0));
        Vector3d target0 = new Vector3d(gravity).sub(new Vector3d(smoothAcc).mul(ACCEL_GAIN)).add(turnAcc);
        target0.y -= bounce * ACCEL_GAIN;
        if (target0.lengthSquared() < 1.0e-6) {
            target0.set(gravity);
        }
        target0.normalize();

        int steps = Math.max(1, (int) Math.ceil(dt * 240));
        double h = dt / steps;
        if (mount) {
            // CharmChain works in model axes (x = json x = screen left).
            if (!LIVE.ready() || LIVE.count() != count) {
                LIVE.reset();
            }
            // Mirror the screen-space rotation across x to get the model-space rotation.
            LIVE.turn(new Quaterniond(partial.x, -partial.y, -partial.z, partial.w));
            Vector3d accView = new Vector3d(gravity).sub(new Vector3d(smoothAcc).mul(ACCEL_GAIN)).add(turnAcc);
            accView.y -= bounce * ACCEL_GAIN;
            Vector3d accModel = toModel(accView).mul(PX_ACCEL);
            // Ornament weight: extra gravity on the last point only, so it hangs lower without stiffening the chain.
            LIVE.setTagPull(toModel(new Vector3d(gravity)).mul(PX_ACCEL * TAG_GRAVITY));
            for (int s = 0; s < steps; s++) {
                LIVE.step(accModel, h);
            }
            LIVE.rotations(rotations);
            return;
        }
        for (int s = 0; s < steps; s++) {
            for (int i = 0; i < count; i++) {
                Vector3d target = i == 0 ? target0 : dir[i - 1];
                double hz = i == 0 ? ROOT_HZ : LINK_HZ;
                double zeta = i == 0 ? ROOT_DAMPING_RATIO : LINK_DAMPING_RATIO;
                double soft = i == 0 ? SOFT_SWING_DEG : SOFT_BEND_DEG;
                step(i, target, hz, zeta, soft, h);
            }
        }
        updateRotations();
    }

    // One damped-spring step pulling segment i toward target.
    private static void step(int i, Vector3d target, double hz, double zeta, double softDeg, double h) {
        Vector3d d = dir[i];
        Vector3d w = omega[i];
        double k = Math.pow(2 * Math.PI * hz, 2);
        double c = 2 * zeta * Math.sqrt(k);
        // Torque axis is d x target, whose length is sin(angle).
        Vector3d axis = new Vector3d(d).cross(target);
        double sin = axis.length();
        double cos = d.dot(target);
        double angle = Math.atan2(sin, cos);
        double extra = 0;
        double soft = Math.toRadians(softDeg);
        if (angle > soft) {
            double over = (angle - soft) / soft;
            extra = over * over * 6;
        }
        Vector3d torque = sin > 1.0e-9 ? axis.mul(angle / sin * k * (1 + extra)) : new Vector3d();
        // Push the segment back out if it swings into the gun body (+x).
        if (!mount && d.x > MAX_INWARD) {
            double push = (d.x - MAX_INWARD) * k * 4;
            torque.add(new Vector3d(d).cross(new Vector3d(-1, 0, 0)).mul(push));
        }
        torque.sub(new Vector3d(w).mul(c * (1 + extra * 0.5)));
        w.add(torque.mul(h));
        // Spin around the segment's own axis does nothing visible, so drop it.
        w.sub(new Vector3d(d).mul(w.dot(d)));
        double wl = w.length();
        if (wl > 1.0e-9) {
            new Quaterniond().fromAxisAngleRad(w.x / wl, w.y / wl, w.z / wl, wl * h).transform(d);
            d.normalize();
        }
    }

    private static Vector3d toLocal(Matrix3d basis, Vector3d world) {
        return new Matrix3d(basis).transpose().transform(world);
    }

    // Converts screen-axis directions (right, up, back) to TACZ model space (x right, y down, z back).
    // The charm hangs along model +y, so each rotation maps (0, 1, 0) onto the segment direction.
    // Segment 0 is absolute; later segments are relative to their parent.
    private static void updateRotations() {
        Quaternionf parentAbs = new Quaternionf();
        for (int i = 0; i < count; i++) {
            Vector3d d = dir[i];
            Quaternionf abs = new Quaternionf().rotationTo(0, 1, 0, (float) d.x, (float) -d.y, (float) d.z);
            if (i == 0) {
                rotations[0].set(abs);
            } else {
                rotations[i].set(new Quaternionf(parentAbs).conjugate().mul(abs));
            }
            parentAbs = abs;
        }
    }
}
