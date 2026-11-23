package com.markwarfare.charm.client;

import com.markwarfare.charm.TaczCharm;
import com.markwarfare.charm.client.CharmGeom.Obb;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * PBD chain simulation for one charm hanging from a gun's charm mount.
 * Coordinates are json-axis px relative to the mount point (charm_pos): x = json x (screen left), y up, z = json z (toward the stock).
 */
// All geometry comes from the models, with no hand-tuned shapes:
// - Gun: every cube as an OBB with its TACZ rotation, plus the mount's rod and screw cubes (see CharmGeom).
// - Charm: each segment uses the cubes of its own bones. The top ring of segment 0 is left out because it is threaded on the mount.
// - Threading: ray scans measure the mount hole radius and wire thickness, and the charm ring's wire thickness and inner width.
//   The constraint keeps the center of the charm ring's wire inside the mount hole.
// - Collision: SAT, pushed back out the side it entered from, then friction.
public final class CharmChain {
    /** Charm shape measured from a charm model. Built once per model. */
    public static final class Charm {
        // Center of the wire at the top of the charm ring, in charm model json space. This is the hanging point.
        public final Vector3d wire;
        public final double wireThick;
        // Inner width of the charm ring along z. Whatever exceeds the mount wire thickness becomes front/back slide room.
        public final double innerZ;
        // Vector from each segment's start to the next segment's start, in rest pose (json).
        public final Vector3d[] rest;
        // Collision boxes per segment, relative to the segment start, in rest pose.
        public final List<List<Obb>> boxes;
        public final boolean measured;
        // Max bend of segment j relative to segment j-1, in radians, measured from the model. Index 0 is unused.
        public final double[] maxBend;

        Charm(Vector3d wire, double wireThick, double innerZ, Vector3d[] rest, List<List<Obb>> boxes, boolean measured) {
            this.wire = wire;
            this.wireThick = wireThick;
            this.innerZ = innerZ;
            this.rest = rest;
            this.boxes = boxes;
            this.measured = measured;
            this.maxBend = new double[rest.length];
            for (int j = 1; j < rest.length; j++) {
                maxBend[j] = bendLimit(j);
            }
        }

        // Bend limit: rotate segment j about its joint in 5° steps and find the angle where it starts to overlap
        // the previous segment. Takes the smallest over 8 directions. Without a limit the tag could fold back
        // more than 150° onto the chain and balance standing upright.
        private double bendLimit(int j) {
            Vector3d axisIn = new Vector3d(rest[j - 1]).normalize();
            Vector3d perp = Math.abs(axisIn.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
            perp.sub(new Vector3d(axisIn).mul(perp.dot(axisIn))).normalize();
            Vector3d joint = new Vector3d(rest[j - 1]);
            double best = Math.PI;
            for (int az = 0; az < 8; az++) {
                Vector3d axis = new Quaterniond().fromAxisAngleRad(axisIn, az * Math.PI / 4).transform(new Vector3d(perp));
                double base = overlap(j, joint, new Matrix3d());
                for (int deg = 5; deg <= 180; deg += 5) {
                    Matrix3d q = new Matrix3d().set(new Quaterniond().fromAxisAngleRad(axis, Math.toRadians(deg)));
                    if (overlap(j, joint, q) > base + 0.02) {
                        best = Math.min(best, Math.toRadians(deg - 5));
                        break;
                    }
                }
            }
            // Capped at 90° because segments are joined by small rings and cannot fold past a right angle.
            // A higher cap (135°) let the tag prop itself up at a slant.
            return Math.max(Math.toRadians(30), Math.min(Math.toRadians(MAX_BEND_DEG), best));
        }

        // Deepest overlap between segment j-1 (start at origin) and segment j (start at joint, rotated by q).
        // Only the halves far from the joint are tested. The boxes already touch near the joint, so testing
        // them too made any small bend count as overlap and the limit always fell to the 30° floor.
        private double overlap(int j, Vector3d joint, Matrix3d q) {
            double worst = 0;
            for (Obb a : boxes.get(j)) {
                // Segment j: the half away from its start (the joint).
                Obb moved = farHalf(a, new Vector3d(), rest[j]).transformed(joint, q);
                for (Obb b : boxes.get(j - 1)) {
                    // Segment j-1: the half away from its end (the joint at rest[j-1]), i.e. toward its start.
                    worst = Math.max(worst, CharmGeom.depth(moved, farHalf(b, rest[j - 1], new Vector3d(rest[j - 1]).negate())));
                }
            }
            return worst;
        }

        // Halves the box along its axis closest to dir and keeps the half farther from `from`.
        private static Obb farHalf(Obb o, Vector3d from, Vector3d dir) {
            Vector3d d = new Vector3d(dir).normalize();
            int k = 0;
            double bestDot = -1;
            for (int i = 0; i < 3; i++) {
                double dot = Math.abs(o.r().getColumn(i, new Vector3d()).dot(d));
                if (dot > bestDot) {
                    bestDot = dot;
                    k = i;
                }
            }
            Vector3d axis = o.r().getColumn(k, new Vector3d());
            double hk = k == 0 ? o.h().x : k == 1 ? o.h().y : o.h().z;
            double side = Math.signum(new Vector3d(o.c()).sub(from).dot(axis));
            if (side == 0) {
                side = Math.signum(axis.dot(d));
            }
            Vector3d c = new Vector3d(o.c()).fma(side * hk / 2, axis);
            Vector3d h = new Vector3d(o.h());
            if (k == 0) {
                h.x /= 2;
            } else if (k == 1) {
                h.y /= 2;
            } else {
                h.z /= 2;
            }
            return new Obb(c, o.r(), h, o.name());
        }

        public int segments() {
            return rest.length;
        }

        public double[] ratios() {
            double[] r = new double[rest.length];
            for (int i = 0; i < r.length; i++) {
                r[i] = rest[i].length();
            }
            return r;
        }
    }

    // Upper bend limit in degrees.
    static final double MAX_BEND_DEG = 90;

    /** The mount ring, already multiplied by the mount scale. wireZ is the ring wire's front-to-back thickness. */
    public record Ring(Vector3d center, double innerRadius, double wireZ, boolean measured) {
    }

    // ---- Reading models ----

    private static final Map<BedrockModel, Charm> CHARMS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public static Charm charmOf(BedrockModel model) {
        return model == null ? null : CHARMS.computeIfAbsent(model, m -> readCharm(m.getIndexBones()));
    }

    // Segment index of a bone: N from the nearest charm_link_N ancestor, or 0 if there is none.
    private static int segmentOf(Map<String, BonesItem> byName, BonesItem b) {
        for (int d = 0; b != null && d < 64; d++) {
            String n = b.getName();
            if (n != null && n.startsWith(CharmLinks.PREFIX)) {
                try {
                    return Integer.parseInt(n.substring(CharmLinks.PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            b = b.getParent() == null ? null : byName.get(b.getParent());
        }
        return 0;
    }

    static Charm readCharm(Map<String, BonesItem> byName) {
        int links = 0;
        while (byName.containsKey(CharmLinks.PREFIX + (links + 1))) {
            links++;
        }
        final int segs = links + 1;
        List<List<Obb>> raw = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            final int seg = i;
            raw.add(CharmGeom.modelBoxes(byName, b -> segmentOf(byName, b) == seg, new Matrix4d(), 0));
        }
        // Charm ring: scan straight down through the middle of segment 0's topmost cubes.
        // The first hit is the top wire's thickness, and the gap below it is the ring hole.
        List<Obb> top = raw.get(0);
        boolean measured = !top.isEmpty();
        double[] bb = measured ? CharmGeom.bounds(top) : new double[]{-0.1, 0.1, -0.1, 0, -0.1, 0.1};
        double topY = bb[3];
        double lx0 = Double.MAX_VALUE, lx1 = -Double.MAX_VALUE, lz0 = Double.MAX_VALUE, lz1 = -Double.MAX_VALUE;
        for (Obb o : top) {
            double[] b = CharmGeom.bounds(List.of(o));
            if (b[3] >= topY - 0.02) {
                lx0 = Math.min(lx0, b[0]);
                lx1 = Math.max(lx1, b[1]);
                lz0 = Math.min(lz0, b[4]);
                lz1 = Math.max(lz1, b[5]);
            }
        }
        double lx = measured ? (lx0 + lx1) / 2 : 0, lz = measured ? (lz0 + lz1) / 2 : 0;
        List<double[]> down = CharmGeom.scan(top, new Vector3d(lx, topY + 1, lz), new Vector3d(0, -1, 0));
        double wireThick = 0.05, holeBottom = topY - 0.35;
        if (!down.isEmpty()) {
            wireThick = down.get(0)[1] - down.get(0)[0];
            if (down.size() > 1) {
                holeBottom = topY + 1 - down.get(1)[0];
            }
        } else {
            measured = false;
        }
        double wireBottom = topY - wireThick;
        double innerZ = 0;
        double midY = (wireBottom + holeBottom) / 2;
        double[] gz = CharmGeom.gapAround(CharmGeom.scan(top, new Vector3d(lx, midY, lz - 5), new Vector3d(0, 0, 1)), 5);
        if (gz != null) {
            innerZ = gz[1] - gz[0];
        }
        Vector3d wire = new Vector3d(lx, topY - wireThick / 2, lz);

        // Segment starts: 0 is the hanging point, i is the pivot of charm_link_i.
        Vector3d[] start = new Vector3d[segs];
        start[0] = wire;
        for (int i = 1; i < segs; i++) {
            List<Float> p = byName.get(CharmLinks.PREFIX + i).getPivot();
            start[i] = p == null || p.size() != 3 ? new Vector3d(wire) : new Vector3d(p.get(0), p.get(1), p.get(2));
        }
        Vector3d[] rest = new Vector3d[segs];
        List<List<Obb>> boxes = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            Vector3d end;
            if (i + 1 < segs) {
                end = start[i + 1];
            } else {
                double[] b = raw.get(i).isEmpty() ? null : CharmGeom.bounds(raw.get(i));
                end = b == null ? new Vector3d(start[i]).sub(0, 1, 0) : new Vector3d((b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[4] + b[5]) / 2);
            }
            rest[i] = new Vector3d(end).sub(start[i]);
            if (rest[i].lengthSquared() < 1.0e-6) {
                rest[i].set(0, -0.5, 0);
            }
            // Group cubes by bone, then merge each group into a few boxes (see cluster).
            // On segment 0, skip everything above the hole bottom: that is the ring threaded on the mount.
            Map<String, List<Obb>> byBone = new java.util.LinkedHashMap<>();
            for (Obb o : raw.get(i)) {
                if (i == 0 && o.c().y > holeBottom) {
                    continue;
                }
                byBone.computeIfAbsent(o.name(), k -> new ArrayList<>()).add(o);
            }
            List<Obb> merged = new ArrayList<>();
            for (Map.Entry<String, List<Obb>> e : byBone.entrySet()) {
                for (double[] b : cluster(e.getValue())) {
                    Vector3d c = new Vector3d((b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[4] + b[5]) / 2).sub(start[i]);
                    Vector3d h = new Vector3d((b[1] - b[0]) / 2, (b[3] - b[2]) / 2, (b[5] - b[4]) / 2).max(new Vector3d(MIN_HALF));
                    merged.add(new Obb(c, new Matrix3d(), h, e.getKey()));
                }
            }
            boxes.add(merged);
        }
        return new Charm(wire, wireThick, innerZ, rest, boxes, measured);
    }

    // Merges one bone's cubes into a few AABBs. One box per bone also blocked the empty space inside big rings
    // and around tag corners. Greedily merges the pair with the least waste (union volume minus both volumes).
    // Merging continues while waste is at most WASTE of the union volume, and past that only while there are
    // more than MAX_BOXES boxes.
    static final int MAX_BOXES = 6;
    static final double WASTE = 0.2;
    // Minimum collision box half-thickness (px), so a very thin chain wire cannot skip through a gun cube in one step.
    static final double MIN_HALF = 0.012;

    static List<double[]> cluster(List<Obb> cubes) {
        List<double[]> b = new ArrayList<>();
        for (Obb o : cubes) {
            b.add(CharmGeom.bounds(List.of(o)));
        }
        while (b.size() > 1) {
            int bi = -1, bj = -1;
            double best = Double.MAX_VALUE, bestRel = 0;
            for (int i = 0; i < b.size(); i++) {
                for (int j = i + 1; j < b.size(); j++) {
                    double[] u = union(b.get(i), b.get(j));
                    double vu = vol(u);
                    double w = vu - vol(b.get(i)) - vol(b.get(j));
                    if (w < best) {
                        best = w;
                        bestRel = vu > 0 ? w / vu : 0;
                        bi = i;
                        bj = j;
                    }
                }
            }
            if (bestRel > WASTE && b.size() <= MAX_BOXES) {
                break;
            }
            double[] u = union(b.get(bi), b.get(bj));
            b.remove(bj);
            b.set(bi, u);
        }
        return b;
    }

    private static double[] union(double[] a, double[] c) {
        return new double[]{Math.min(a[0], c[0]), Math.max(a[1], c[1]), Math.min(a[2], c[2]), Math.max(a[3], c[3]), Math.min(a[4], c[4]), Math.max(a[5], c[5])};
    }

    private static double vol(double[] a) {
        return Math.max(0, a[1] - a[0]) * Math.max(0, a[3] - a[2]) * Math.max(0, a[5] - a[4]);
    }

    private static Map<String, BonesItem> mountBones(int part) {
        return TimelessAPI.getClientAttachmentIndex(ResourceLocation.fromNamespaceAndPath(TaczCharm.MOD_ID, TaczCharm.MOUNT_PART_IDS[part]))
                .map(i -> ((BedrockModel) i.getAttachmentModel()).getIndexBones())
                .orElse(null);
    }

    private static Ring ringRaw;

    // Mount ring at scale 1. Vertical and horizontal scans through the origin give the hole center and radius,
    // and a z scan just below the hole gives the bottom wire's front-to-back thickness.
    private static Ring ringRaw() {
        if (ringRaw != null) {
            return ringRaw;
        }
        Map<String, BonesItem> bones = mountBones(0);
        if (bones == null) {
            return new Ring(new Vector3d(), 0.19, 0.12, false);
        }
        List<Obb> boxes = CharmGeom.modelBoxes(bones, b -> true, new Matrix4d(), 0);
        double[] gy = CharmGeom.gapAround(CharmGeom.scan(boxes, new Vector3d(0, -5, 0), new Vector3d(0, 1, 0)), 5);
        if (gy == null) {
            TaczCharm.LOG.warn("참 마운트 고리 구멍을 못 찾음 — 예전 값(0.19) 씀");
            return ringRaw = new Ring(new Vector3d(), 0.19, 0.12, false);
        }
        double cy = -5 + (gy[0] + gy[1]) / 2;
        double[] gx = CharmGeom.gapAround(CharmGeom.scan(boxes, new Vector3d(-5, cy, 0), new Vector3d(1, 0, 0)), 5);
        double cx = gx == null ? 0 : -5 + (gx[0] + gx[1]) / 2;
        double r = (gy[1] - gy[0]) / 2;
        if (gx != null) {
            r = Math.min(r, (gx[1] - gx[0]) / 2);
        }
        double bottom = -5 + gy[0] - 0.01;
        List<double[]> zs = CharmGeom.scan(boxes, new Vector3d(cx, bottom, -5), new Vector3d(0, 0, 1));
        double wz = 0.12, cz = 0;
        for (double[] s : zs) {
            if (s[0] <= 5 + 1 && s[1] >= 5 - 1) {
                wz = s[1] - s[0];
                cz = -5 + (s[0] + s[1]) / 2;
                break;
            }
        }
        ringRaw = new Ring(new Vector3d(cx, cy, cz), r, wz, true);
        TaczCharm.LOG.info("참 마운트 고리 잼: 가운데 ({}, {}, {}) 안쪽 반지름 {} 테 앞뒤 {}",
                fmt(cx), fmt(cy), fmt(cz), fmt(r), fmt(wz));
        return ringRaw;
    }

    public static Ring ring(double scale) {
        Ring r = ringRaw();
        return new Ring(new Vector3d(r.center()).mul(scale), r.innerRadius() * scale, r.wireZ() * scale, r.measured());
    }

    /**
     * Gun cubes in mount space plus the mount's rod and screw cubes. forward is a front/back offset in px, k is the mount scale.
     * The mount ring itself is excluded; the threading constraint handles it.
     */
    public static List<Obb> wall(Map<String, BonesItem> gun, float forward, float k) {
        BonesItem pos = gun.get(CharmNodes.POS);
        List<Obb> out = new ArrayList<>();
        if (pos == null || pos.getPivot() == null) {
            return out;
        }
        Matrix4d toLocal = CharmGeom.toBoneLocal(gun, pos, new Vector3d(0, 0, -forward));
        out.addAll(CharmGeom.modelBoxes(gun, b -> !CharmNodes.excludedForCharm(gun, b), toLocal, 10));
        for (int part = 1; part < 3; part++) {
            Map<String, BonesItem> mb = mountBones(part);
            if (mb != null) {
                out.addAll(CharmGeom.modelBoxes(mb, b -> true, new Matrix4d().scaling(k), 0));
            }
        }
        return out;
    }

    // ---- State ----

    private Charm charm;
    private Ring ring;
    private List<Obb> wall = List.of();
    private List<Obb> wallSource;
    private double ringR;
    private double zSlack;
    private Vector3d[] pts = new Vector3d[0];
    private Vector3d[] prev = new Vector3d[0];
    private double[] len = new double[0];
    private final Vector3d attach = new Vector3d();
    private final Vector3d attachVel = new Vector3d();
    private boolean ready;
    // Outward normal and depth of each point's deepest contact this step, used by friction().
    private Vector3d[] contactN = new Vector3d[0];
    private double[] contactD = new double[0];

    // Hanging point inside the mount ring: gravity (px/s²), rim bounce (restitution), and rim friction
    // (fraction of velocity left per second).
    private static final double ATTACH_GRAVITY = 60;
    private static final double ATTACH_BOUNCE = 0.3;
    private static final double ATTACH_RIM_FRICTION = 0.02;
    // Velocity damping (1/s). Higher values (3.5) made the chain look stiff; lower values let it clip
    // through the gun more often when shaken hard.
    private static final double DAMP = 2.5;
    // Segment mass is its base (length or box volume, see MASS_BY_LENGTH) times TAG_MASS for the last segment
    // (the tag) or LINK_MASS for chain links. Each point gets half of each adjacent segment's mass.
    // Distance and bend corrections are split by inverse mass, so the light chain bends to follow the heavy tag.
    private static final double TAG_MASS = 4;
    private static final double LINK_MASS = 0.5;
    // Mass basis: length by default, or box volume with massMode=volume. By volume a small chain link is about
    // 1/10000 of the tag and ends up nearly massless.
    private static final boolean MASS_BY_LENGTH = true;
    // Inverse mass per point. 0 for the hanging point, which is driven by stepAttach instead.
    private double[] invMass = new double[0];
    private double[] mass = new double[0];
    private static final int ITER = 10;
    // Gap kept between boxes (px) so faces don't flicker.
    private static final double MARGIN = 0.02;
    // Max push per correction (px). Iterations get the point out gradually; pushing all at once made it jump.
    private static final double PUSH_MAX = 0.3;
    // Point positions before this step, for the work term of the energy rule.
    private Vector3d[] before = new Vector3d[0];
    private double lastH;
    private double[] pushUsed = new double[0];
    private Vector3d[] snap = new Vector3d[0];
    private final Vector3d snapAttach = new Vector3d();
    // Off. It slightly reduces jitter at rest but makes
    // clipping worse when the gun is shaken.
    private static final boolean GUARD = false;
    // "At rest" test: max rate of change of the pull acceleration (px/s³) and how long it must hold (s).
    private static final double CALM_ACC = 50;
    private static final double CALM_SECONDS = 0.3;
    private double calmTime;
    private final Vector3d lastAcc = new Vector3d();
    // Effectively off by default. A small budget (0.06 px) almost removes jitter at rest, but clipping during
    // hard shakes gets much worse.
    private static final double STEP_PUSH_MAX = 1000;
    // Energy debt (px²/s², masses normalized to average 1). Capped, because a large debt would keep the chain
    // from moving even when shaken.
    private double energyDebt;
    private static final double DEBT_MAX = 200;
    // Static and kinetic friction, scaled by contact depth. Kept low for metal on metal: higher values (0.5/0.3)
    // let a tag resting on a rail hold against the chain's pull instead of sliding off.
    private static final double MU_STATIC = 0.15;
    private static final double MU_KINETIC = 0.1;

    /** Sets the charm, mount ring, and collision cubes. Re-hangs the chain if any of them changed. */
    public void set(Charm charm, Ring ring, List<Obb> wall) {
        boolean changed = charm != this.charm || wall != this.wallSource || ring == null || this.ring == null
                || !ring.center().equals(this.ring.center()) || ring.innerRadius() != this.ring.innerRadius();
        this.charm = charm;
        this.ring = ring;
        if (changed) {
            // Keep only cubes within reach: ring radius + chain length + largest charm box extent.
            double reach = ring.innerRadius() + 0.5;
            double boxReach = 0;
            for (int i = 0; i < charm.segments(); i++) {
                reach += charm.rest[i].length();
                for (Obb o : charm.boxes.get(i)) {
                    boxReach = Math.max(boxReach, o.c().length() + o.radius());
                }
            }
            reach += boxReach;
            List<Obb> near = new ArrayList<>();
            for (Obb g : wall) {
                if (g.c().distance(ring.center()) - g.radius() <= reach) {
                    near.add(g);
                }
            }
            this.wallSource = wall;
            this.wall = near;
        }
        // Threading: the charm wire's center stays within the hole radius minus half the wire thickness.
        // Along z it may slide by (charm ring inner width - mount wire thickness) / 2.
        ringR = Math.max(0.001, ring.innerRadius() - charm.wireThick / 2);
        zSlack = Math.max(0, (charm.innerZ - ring.wireZ()) / 2);
        if (changed) {
            ready = false;
        }
    }

    public boolean ready() {
        return ready && charm != null;
    }

    public void invalidate() {
        ready = false;
    }

    public Vector3d attach() {
        return attach;
    }

    public Charm charm() {
        return charm;
    }

    public double ringRadius() {
        return ringR;
    }

    public Vector3d point(int i) {
        return pts[i];
    }

    public int count() {
        return charm == null ? 0 : charm.segments();
    }

    /**
     * Hangs the chain from the bottom of the ring. Starts straight down and tilts in 10° steps over 8 directions
     * until the chain no longer touches the gun.
     */
    public void reset() {
        int n = charm.segments();
        pts = new Vector3d[n + 1];
        prev = new Vector3d[n + 1];
        len = charm.ratios();
        contactN = new Vector3d[n + 1];
        contactD = new double[n + 1];
        // Segment masses -> point masses, normalized to average 1 so energy limits stay independent of the mass settings.
        double[] seg = new double[n];
        for (int i = 0; i < n; i++) {
            double v = 0;
            for (Obb o : charm.boxes.get(i)) {
                v += 8 * o.h().x * o.h().y * o.h().z;
            }
            double base = MASS_BY_LENGTH ? charm.rest[i].length() : Math.max(v, 1.0e-4);
            seg[i] = base * (i == n - 1 && n > 1 ? TAG_MASS : LINK_MASS);
        }
        mass = new double[n + 1];
        invMass = new double[n + 1];
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            mass[i] = 0.5 * seg[i - 1] + (i < n ? 0.5 * seg[i] : 0);
            sum += mass[i];
        }
        for (int i = 1; i <= n; i++) {
            mass[i] = sum > 0 ? mass[i] * n / sum : 1;
            invMass[i] = 1 / mass[i];
        }
        attach.set(ring.center()).sub(0, ringR, 0);
        attachVel.zero();
        lastH = 0;
        energyDebt = 0;
        calmTime = 0;
        pts[0] = new Vector3d(attach);
        boolean found = false;
        for (int tilt = 0; tilt <= 80 && !found; tilt += 10) {
            for (int az = 0; az < (tilt == 0 ? 1 : 8) && !found; az++) {
                double t = Math.toRadians(tilt), a = Math.toRadians(az * 45);
                Vector3d d = new Vector3d(Math.sin(t) * Math.cos(a), -Math.cos(t), Math.sin(t) * Math.sin(a));
                for (int i = 0; i < n; i++) {
                    pts[i + 1] = new Vector3d(pts[i]).fma(len[i], d);
                }
                found = penetration() <= 0;
            }
        }
        for (int i = 0; i <= n; i++) {
            prev[i] = new Vector3d(pts[i]);
        }
        for (int k = 0; k < 20; k++) {
            java.util.Arrays.fill(pushUsed, 0);
            constrain();
        }
        for (int i = 0; i <= n; i++) {
            prev[i].set(pts[i]);
        }
        ready = true;
    }

    /** Rotates the chain with the view so it keeps its inertia. q is a rotation in model axes. */
    public void turn(Quaterniond q) {
        for (int i = 1; i < pts.length; i++) {
            q.transform(pts[i]);
            q.transform(prev[i]);
        }
    }

    // Extra gravity on the ornament only (model axes, px/s²), set each frame by CharmPhysics.
    // The chain keeps its motion; the heavy ornament just settles lower instead of floating out with the swing.
    private final Vector3d tagPull = new Vector3d();

    public void setTagPull(Vector3d pull) {
        tagPull.set(pull);
    }

    /** Advances one step of h seconds. acc is the pull on the chain (gravity plus gun motion), in model axes, px/s². */
    public void step(Vector3d acc, double h) {
        int n = pts.length - 1;
        stepAttach(acc, h);
        double damp = Math.exp(-DAMP * h);
        // Frame dt varies, so rescale the previous displacement to this step's length.
        // Otherwise the implied velocity jumps by h / lastH.
        double scale = lastH > 0 ? h / lastH : 1;
        if (before.length != n + 1) {
            before = new Vector3d[n + 1];
            for (int i = 0; i <= n; i++) {
                before[i] = new Vector3d();
            }
        }
        double keOld = 0;
        prev[0].set(pts[0]);
        for (int i = 1; i <= n; i++) {
            Vector3d p = pts[i];
            Vector3d v = new Vector3d(p).sub(prev[i]).mul(damp * scale);
            keOld += mass[i] * v.lengthSquared() / (h * h) * 0.5;
            before[i].set(p);
            prev[i].set(p);
            p.add(v).fma(h * h, acc);
            if (i == n && n > 1) {
                p.fma(h * h, tagPull);
            }
        }
        java.util.Arrays.fill(contactD, 0);
        java.util.Arrays.fill(pushUsed, 0);
        // Divergence guard: if an iteration increases total penetration, undo it and stop iterating.
        // A tag caught on an edge can lever around as it is pushed, digging its other side in deeper.
        // Only active at rest (pull nearly constant for CALM_SECONDS); while shaking it made clipping much worse.
        calmTime = lastAcc.distance(acc) < CALM_ACC * h ? calmTime + h : 0;
        lastAcc.set(acc);
        boolean guard = GUARD && calmTime > CALM_SECONDS;
        double pen = guard ? penetration() : 0;
        if (snap.length != n + 1) {
            snap = new Vector3d[n + 1];
            for (int i = 0; i <= n; i++) {
                snap[i] = new Vector3d();
            }
        }
        for (int k = 0; k < ITER; k++) {
            if (guard) {
                for (int i = 0; i <= n; i++) {
                    snap[i].set(pts[i]);
                }
                snapAttach.set(attach);
            }
            constrain();
            if (guard) {
                double after = penetration();
                if (after > pen + 1.0e-4) {
                    for (int i = 0; i <= n; i++) {
                        pts[i].set(snap[i]);
                    }
                    attach.set(snapAttach);
                    break;
                }
                pen = after;
            }
        }
        friction();
        // Energy rule: kinetic energy may only grow by the work done by the pull (gravity and gun motion).
        // In PBD, position corrections from collision, distance, and bend constraints turn into velocity.
        // Without this cap the tag could launch itself above the chain while the gun was held still.
        double keNew = 0, work = 0;
        for (int i = 1; i <= n; i++) {
            keNew += mass[i] * new Vector3d(pts[i]).sub(prev[i]).lengthSquared() / (h * h) * 0.5;
            work += mass[i] * acc.dot(new Vector3d(pts[i]).sub(before[i]));
            if (i == n && n > 1) {
                work += mass[i] * tagPull.dot(new Vector3d(pts[i]).sub(before[i]));
            }
        }
        // When collision lifts the chain against the pull (negative work), carry the deficit as debt and pay it
        // from later steps. This breaks a loop where a tag resting on the gun got lifted for free, fell, and
        // gained speed, bouncing periodically.
        double limit = keOld + work - energyDebt;
        energyDebt = limit < 0 ? Math.min(-limit, DEBT_MAX) : 0;
        if (keNew > limit && keNew > 1.0e-12) {
            double s = Math.sqrt(Math.max(0, limit) / keNew);
            for (int i = 1; i <= n; i++) {
                Vector3d move = new Vector3d(pts[i]).sub(prev[i]).mul(s);
                prev[i].set(pts[i]).sub(move);
            }
        }
        lastH = h;
    }

    // The hanging point falls toward the pull and slides along the hole rim, limited by the z slack.
    private void stepAttach(Vector3d acc, double h) {
        Vector3d pull = new Vector3d(acc);
        if (pull.lengthSquared() < 1.0e-12) {
            pull.set(0, -1, 0);
        }
        pull.normalize();
        attachVel.fma(ATTACH_GRAVITY * h, pull);
        attach.fma(h, attachVel);
        clampAttach(true, h);
    }

    private void clampAttach(boolean bounce, double h) {
        Vector3d c = ring.center();
        double dx = attach.x - c.x, dy = attach.y - c.y;
        double r = Math.sqrt(dx * dx + dy * dy);
        if (r > ringR) {
            double nx = dx / r, ny = dy / r;
            attach.x = c.x + nx * ringR;
            attach.y = c.y + ny * ringR;
            if (bounce) {
                double vn = attachVel.x * nx + attachVel.y * ny;
                if (vn > 0) {
                    attachVel.x -= (1 + ATTACH_BOUNCE) * vn * nx;
                    attachVel.y -= (1 + ATTACH_BOUNCE) * vn * ny;
                }
                attachVel.mul(Math.pow(ATTACH_RIM_FRICTION, h));
            }
        }
        double dz = attach.z - c.z;
        if (Math.abs(dz) > zSlack) {
            attach.z = c.z + Math.signum(dz) * zSlack;
            if (bounce) {
                attachVel.z = 0;
            }
        }
    }

    private Matrix3d orientation(Vector3d a, Vector3d b, int i) {
        Vector3d d = new Vector3d(b).sub(a);
        if (d.lengthSquared() < 1.0e-12) {
            return new Matrix3d();
        }
        return new Matrix3d().set(segQuat(a, d.normalize(), i));
    }

    // Max tilt of the charm ring toward the mount wire direction, in degrees. Past this the two rings would intersect.
    private static final double RING_TILT_DEG = 25;

    // Direction of the mount wire at the hanging point: the tangent of the ring circle in the xy plane.
    private Vector3d wireTangent(Vector3d at) {
        double rx = at.x - ring.center().x, ry = at.y - ring.center().y;
        double r = Math.sqrt(rx * rx + ry * ry);
        if (r < 1.0e-6) {
            return new Vector3d(1, 0, 0);
        }
        return new Vector3d(-ry / r, rx / r, 0);
    }

    // Absolute segment rotation in model axes. Aligning only the chain direction leaves segment 0's twist free,
    // and the charm ring could spin until it passed through the mount ring. So segment 0 is also twisted about
    // the chain direction until its ring normal (rest-pose x) lines up with the mount wire, keeping the two rings
    // perpendicular like real interlocked rings.
    private Quaterniond segQuat(Vector3d a, Vector3d dn, int i) {
        Quaterniond q = new Quaterniond().rotationTo(new Vector3d(charm.rest[i]).normalize(), dn);
        if (i != 0 || ring == null) {
            return q;
        }
        Vector3d t = wireTangent(a);
        Vector3d tp = new Vector3d(t).sub(new Vector3d(dn).mul(dn.dot(t)));
        if (tp.lengthSquared() < 1.0e-8) {
            return q;
        }
        tp.normalize();
        Vector3d nrm = q.transform(new Vector3d(1, 0, 0));
        if (nrm.dot(tp) < 0) {
            tp.negate();
        }
        double phi = Math.atan2(dn.dot(new Vector3d(nrm).cross(tp)), nrm.dot(tp));
        return new Quaterniond().fromAxisAngleRad(dn, phi).mul(q);
    }

    // Clamps segment 0 so it tilts at most RING_TILT_DEG toward the mount wire. The ring can swing but stays wrapped around the wire.
    private void limitRingTilt() {
        if (ring == null || pts.length < 2) {
            return;
        }
        Vector3d d = new Vector3d(pts[1]).sub(pts[0]);
        double l = d.length();
        if (l < 1.0e-9) {
            return;
        }
        Vector3d t = wireTangent(pts[0]);
        double s = d.dot(t) / l, max = Math.sin(Math.toRadians(RING_TILT_DEG));
        if (Math.abs(s) <= max) {
            return;
        }
        Vector3d perp = new Vector3d(d).sub(new Vector3d(t).mul(d.dot(t)));
        if (perp.lengthSquared() < 1.0e-12) {
            perp.set(0, -1, 0);
        }
        perp.normalize().mul(Math.sqrt(1 - max * max)).add(new Vector3d(t).mul(Math.signum(s) * max));
        pts[1].set(pts[0]).add(perp.mul(l));
    }

    // One solver pass: distance constraints, ring tilt, bend limits, then collision.
    private void constrain() {
        int n = pts.length - 1;
        pts[0].set(attach);
        for (int i = 0; i < n; i++) {
            Vector3d a = pts[i], b = pts[i + 1];
            Vector3d d = new Vector3d(b).sub(a);
            double l = d.length();
            if (l < 1.0e-9) {
                d.set(0, -1, 0);
                l = 1.0e-9;
            }
            double diff = (l - len[i]) / l;
            if (i == 0) {
                b.sub(new Vector3d(d).mul(diff));
            } else {
                double fa = invMass[i] / (invMass[i] + invMass[i + 1]);
                a.add(new Vector3d(d).mul(diff * fa));
                b.sub(new Vector3d(d).mul(diff * (1 - fa)));
            }
        }
        limitRingTilt();
        // Bend limits measured from the model: rotate both segments back toward each other when exceeded.
        for (int j = 1; j < n; j++) {
            Vector3d d1 = new Vector3d(pts[j]).sub(pts[j - 1]);
            Vector3d d2 = new Vector3d(pts[j + 1]).sub(pts[j]);
            double l1 = d1.length(), l2 = d2.length();
            if (l1 < 1.0e-9 || l2 < 1.0e-9) {
                continue;
            }
            double ang = Math.acos(Math.max(-1, Math.min(1, d1.dot(d2) / (l1 * l2))));
            double max = charm.maxBend[j];
            if (ang > max) {
                Vector3d axis = new Vector3d(d1).cross(d2);
                if (axis.lengthSquared() < 1.0e-18) {
                    // Folded exactly back on itself: any perpendicular axis works.
                    axis = Math.abs(d1.x) < 0.9 * l1 ? new Vector3d(1, 0, 0).cross(d1) : new Vector3d(0, 0, 1).cross(d1);
                }
                axis.normalize();
                // Split the excess angle by inverse mass. If point j+1 is heavy (the tag), the lighter link before it does most of the turning.
                double f2 = invMass[j + 1] / (invMass[j] + invMass[j + 1]);
                double over = ang - max;
                Vector3d dir2 = new Quaterniond().fromAxisAngleRad(axis, -over * f2).transform(new Vector3d(d2));
                new Quaterniond().fromAxisAngleRad(axis, over * (1 - f2)).transform(d1);
                pts[j].set(pts[j - 1]).add(d1);
                pts[j + 1].set(pts[j]).add(dir2);
            }
        }
        for (int i = 0; i < n; i++) {
            collideSegment(i);
        }
    }

    private void collideSegment(int i) {
        Vector3d a = pts[i], b = pts[i + 1];
        Matrix3d q = orientation(a, b, i);
        Matrix3d qp = orientation(prev[i], prev[i + 1], i);
        for (Obb local : charm.boxes.get(i)) {
            Obb now = local.transformed(a, q);
            Obb before = local.transformed(prev[i], qp);
            double reach = now.radius();
            for (Obb g : wall) {
                if (now.c().distance(g.c()) > reach + g.radius() + MARGIN) {
                    continue;
                }
                double[] p = CharmGeom.push(now, before, g, MARGIN);
                if (p == null) {
                    continue;
                }
                Vector3d nrm = new Vector3d(p[0], p[1], p[2]);
                double depth = p[3];
                // Split the push between the segment's two points by where the box center lies along the segment (t).
                Vector3d ab = new Vector3d(b).sub(a);
                double t = Math.max(0.05, Math.min(2, new Vector3d(now.c()).sub(a).dot(ab) / Math.max(1.0e-9, ab.lengthSquared())));
                double wa = i == 0 ? 0 : 1;
                // Near the fixed hanging point (small t) the end point would need a push of depth / t, often several px,
                // which made the chain jump. Capped at 2 * depth / t.
                double lambda = Math.min(depth / (wa * (1 - t) * (1 - t) + t * t), depth * 2 / Math.max(t, 1.0e-3));
                Vector3d db = budget(i + 1, clamp(new Vector3d(nrm).mul(t * lambda)));
                b.add(db);
                if (wa > 0) {
                    a.add(budget(i, clamp(new Vector3d(nrm).mul((1 - t) * lambda))));
                } else if (t < 0.5) {
                    // A box right next to the hanging point can't escape by rotating the end point alone,
                    // so move the hanging point within the ring as well.
                    attach.add(clamp(new Vector3d(nrm).mul(depth * (1 - t))));
                    clampAttach(false, 0);
                    a.set(attach);
                }
                now = local.transformed(a, orientation(a, b, i));
                record(i + 1, nrm, depth);
                if (i > 0) {
                    record(i, nrm, depth);
                }
            }
        }
    }

    // Limits the total push a point can receive in one step (STEP_PUSH_MAX). The rest carries over to the next step.
    // Meant to stop a tag levering on an edge from teleporting within one step; effectively disabled by default.
    private Vector3d budget(int i, Vector3d v) {
        if (pushUsed.length != pts.length) {
            pushUsed = new double[pts.length];
        }
        double l = v.length();
        double left = STEP_PUSH_MAX - pushUsed[i];
        if (left <= 0) {
            return v.zero();
        }
        if (l > left) {
            v.mul(left / l);
            l = left;
        }
        pushUsed[i] += l;
        return v;
    }

    private static Vector3d clamp(Vector3d v) {
        double l = v.length();
        return l > PUSH_MAX ? v.mul(PUSH_MAX / l) : v;
    }

    private void record(int i, Vector3d n, double d) {
        if (d > contactD[i]) {
            contactD[i] = d;
            contactN[i] = new Vector3d(n);
        }
    }

    // Contact friction for points that touched this step. Tangential motion is reduced in proportion to contact depth.
    // Small motion is cancelled entirely (static friction).
    private void friction() {
        for (int i = 1; i < pts.length; i++) {
            if (contactD[i] <= 0) {
                continue;
            }
            Vector3d n = contactN[i];
            Vector3d move = new Vector3d(pts[i]).sub(prev[i]);
            // Zero restitution. In PBD the push-out distance becomes outward velocity, which made the chain
            // bounce on every contact and fly up over the ring.
            double vn = move.dot(n);
            if (vn > 0) {
                prev[i].fma(vn, n);
                move.fma(-vn, n);
            }
            Vector3d tan = new Vector3d(move).sub(new Vector3d(n).mul(move.dot(n)));
            double tl = tan.length();
            if (tl < 1.0e-12) {
                continue;
            }
            double d = contactD[i];
            if (tl < MU_STATIC * d) {
                prev[i].add(tan);
            } else {
                prev[i].add(tan.mul(Math.min(1, MU_KINETIC * d / tl)));
            }
        }
    }

    /** Deepest penetration of any charm box into a gun cube, in px. 0 means no contact. Used by automated tests. */
    public double penetration() {
        double worst = 0;
        int n = pts.length - 1;
        for (int i = 0; i < n; i++) {
            if (pts[i + 1] == null) {
                continue;
            }
            Matrix3d q = orientation(pts[i], pts[i + 1], i);
            for (Obb local : charm.boxes.get(i)) {
                Obb now = local.transformed(pts[i], q);
                for (Obb g : wall) {
                    if (now.c().distance(g.c()) > now.radius() + g.radius()) {
                        continue;
                    }
                    worst = Math.max(worst, CharmGeom.depth(now, g));
                }
            }
        }
        return worst;
    }

    /** Debug: bone names and bounds of gun cubes within 0.05 px of any charm box. */
    public String touching() {
        StringBuilder sb = new StringBuilder();
        java.util.Set<Obb> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int n = pts.length - 1;
        for (int i = 0; i < n; i++) {
            Matrix3d q = orientation(pts[i], pts[i + 1], i);
            for (Obb local : charm.boxes.get(i)) {
                Obb now = local.transformed(pts[i], q);
                Obb grown = new Obb(now.c(), now.r(), new Vector3d(now.h()).add(0.05, 0.05, 0.05), now.name());
                for (Obb g : wall) {
                    if (!seen.contains(g) && CharmGeom.depth(grown, g) > 0) {
                        seen.add(g);
                        double[] b = CharmGeom.bounds(List.of(g));
                        sb.append(String.format(" [마디%d %s x %.2f~%.2f y %.2f~%.2f z %.2f~%.2f]", i, g.name(), b[0], b[1], b[2], b[3], b[4], b[5]));
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Segment rotations for rendering, in render space (y flipped). Segment 0 is absolute; each later segment is relative to its parent.
     */
    public void rotations(Quaternionf[] out) {
        int n = pts.length - 1;
        Quaternionf parentAbs = new Quaternionf();
        for (int i = 0; i < n && i < out.length; i++) {
            Vector3d d = new Vector3d(pts[i + 1]).sub(pts[i]);
            Quaternionf abs;
            if (d.lengthSquared() < 1.0e-12) {
                abs = new Quaternionf();
            } else {
                // Model axes to render space with the y flip M: M * R * M is the quaternion (-x, y, -z, w).
                Quaterniond m = segQuat(pts[i], new Vector3d(d).normalize(), i);
                abs = new Quaternionf((float) -m.x, (float) m.y, (float) -m.z, (float) m.w);
            }
            if (i == 0) {
                out[0].set(abs);
            } else {
                out[i].set(new Quaternionf(parentAbs).conjugate().mul(abs));
            }
            parentAbs = abs;
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder(String.format("꿰임 반지름 %.3f 앞뒤 %.3f 점:", ringR, zSlack));
        for (Vector3d p : pts) {
            sb.append(String.format(" (%.2f, %.2f, %.2f)", p.x, p.y, p.z));
        }
        return sb.toString();
    }

    static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
