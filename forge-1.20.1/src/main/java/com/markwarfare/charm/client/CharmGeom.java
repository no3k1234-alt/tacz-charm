package com.markwarfare.charm.client;

import com.tacz.guns.client.resource.pojo.model.BonesItem;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Collision geometry for charms. Model cubes become oriented boxes (OBBs) in json-axis px (x = json x, y up, z = json z).
 */
// TACZ rotation convention (from BedrockModel/BedrockPart): bones and cubes rotate about their pivot in render
// space (y flipped) as Rz * Ry * Rx, using the json degrees with signs unchanged. In json axes that is F * M * F,
// where F is the y flip.
public final class CharmGeom {
    private CharmGeom() {
    }

    /** Oriented box: center c, axes r (columns are the box's unit x, y, z), half extents h. */
    public record Obb(Vector3d c, Matrix3d r, Vector3d h, String name) {
        public double radius() {
            return h.length();
        }

        public Obb transformed(Vector3d t, Matrix3d q) {
            return new Obb(new Vector3d(c).mul(q).add(t), new Matrix3d(q).mul(r), h, name);
        }
    }

    // ---- TACZ transforms ----

    /** Converts a TACZ bone or cube rotation (degrees) to a rotation matrix in json axes. */
    public static Matrix3d taczRotation(List<Float> deg) {
        Matrix3d m = new Matrix3d();
        if (deg == null || deg.size() != 3) {
            return m;
        }
        Matrix3d render = new Matrix3d()
                .rotateZ(Math.toRadians(deg.get(2)))
                .rotateY(Math.toRadians(deg.get(1)))
                .rotateX(Math.toRadians(deg.get(0)));
        Matrix3d f = new Matrix3d().scaling(1, -1, 1);
        return m.set(f).mul(render).mul(f);
    }

    private static Vector3d vec(List<Float> l) {
        return l == null || l.size() != 3 ? new Vector3d() : new Vector3d(l.get(0), l.get(1), l.get(2));
    }

    /** Per-bone transform in json space, including ancestors: A_b = A_parent * T(p_b) * R_b * T(-p_b). */
    public static Map<String, Matrix4d> boneWorld(Map<String, BonesItem> byName) {
        Map<String, Matrix4d> out = new HashMap<>();
        for (BonesItem b : byName.values()) {
            world(byName, b, out, 0);
        }
        return out;
    }

    private static Matrix4d world(Map<String, BonesItem> byName, BonesItem b, Map<String, Matrix4d> memo, int depth) {
        Matrix4d m = memo.get(b.getName());
        if (m != null) {
            return m;
        }
        BonesItem parent = b.getParent() == null ? null : byName.get(b.getParent());
        Matrix4d base = parent != null && depth < 64 ? new Matrix4d(world(byName, parent, memo, depth + 1)) : new Matrix4d();
        Vector3d p = vec(b.getPivot());
        m = base.translate(p).mul(new Matrix4d().set(taczRotation(b.getRotation()))).translate(-p.x, -p.y, -p.z);
        memo.put(b.getName(), m);
        return m;
    }

    // One cube as an OBB: cube rotation, then the bone's world transform, then toLocal.
    private static Obb cubeObb(CubesItem cube, Matrix4d boneWorld, Matrix4d toLocal, String name) {
        Vector3d o = vec(cube.getOrigin());
        Vector3d s = vec(cube.getSize());
        double inf = cube.getInflate();
        Matrix4d m = new Matrix4d(toLocal).mul(boneWorld);
        if (cube.getRotation() != null && cube.getRotation().size() == 3) {
            Vector3d cp = cube.getPivot() != null && cube.getPivot().size() == 3 ? vec(cube.getPivot()) : new Vector3d(o).fma(0.5, s);
            m.translate(cp).mul(new Matrix4d().set(taczRotation(cube.getRotation()))).translate(-cp.x, -cp.y, -cp.z);
        }
        Vector3d center = m.transformPosition(new Vector3d(o).fma(0.5, s));
        Matrix3d rot = new Matrix3d();
        m.get3x3(rot);
        // Column lengths are per-axis scale (e.g. the mount scale k). Move them into the half extents and
        // normalize the axes. Scaling only the center would leave a scaled-down mount rod at full length.
        Vector3d ax = new Vector3d(), ay = new Vector3d(), az = new Vector3d();
        rot.getColumn(0, ax);
        rot.getColumn(1, ay);
        rot.getColumn(2, az);
        double sx = ax.length(), sy = ay.length(), sz = az.length();
        ax.normalize();
        ay.normalize();
        az.normalize();
        rot.setColumn(0, ax).setColumn(1, ay).setColumn(2, az);
        Vector3d half = new Vector3d((Math.abs(s.x) / 2 + inf) * sx, (Math.abs(s.y) / 2 + inf) * sy, (Math.abs(s.z) / 2 + inf) * sz);
        return new Obb(center, rot, half, name);
    }

    /**
     * OBBs for every cube of the bones accepted by keep, transformed by toLocal.
     * within: keep only boxes that reach within this distance of the origin (px). 0 or less keeps all.
     */
    public static List<Obb> modelBoxes(Map<String, BonesItem> byName, Predicate<BonesItem> keep, Matrix4d toLocal, double within) {
        Map<String, Matrix4d> worlds = boneWorld(byName);
        List<Obb> out = new ArrayList<>();
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || !keep.test(bone)) {
                continue;
            }
            Matrix4d bw = worlds.get(bone.getName());
            for (CubesItem cube : bone.getCubes()) {
                if (cube.getOrigin() == null || cube.getSize() == null || cube.getOrigin().size() != 3 || cube.getSize().size() != 3) {
                    continue;
                }
                Obb box = cubeObb(cube, bw, toLocal, bone.getName());
                if (within > 0 && box.c().length() - box.radius() > within) {
                    continue;
                }
                out.add(box);
            }
        }
        return out;
    }

    /** Matrix into bone b's local space: b's pivot (plus extraShift) at the origin, axes as rendered under b. */
    public static Matrix4d toBoneLocal(Map<String, BonesItem> byName, BonesItem b, Vector3d extraShift) {
        Matrix4d w = boneWorld(byName).get(b.getName());
        Vector3d p = vec(b.getPivot()).add(extraShift);
        // World to bone space: apply A_b^-1, then move the pivot to the origin.
        return new Matrix4d().translation(-p.x, -p.y, -p.z).mul(new Matrix4d(w).invert());
    }

    // ---- SAT (separating axis test) ----

    // Up to 15 candidate axes: 3 from a, 3 from b, and 9 cross products. Near-parallel pairs give a zero cross product and are skipped.
    private static int axes(Obb a, Obb b, Vector3d[] out) {
        int n = 0;
        Vector3d[] ua = new Vector3d[3], ub = new Vector3d[3];
        for (int i = 0; i < 3; i++) {
            ua[i] = a.r().getColumn(i, new Vector3d());
            ub[i] = b.r().getColumn(i, new Vector3d());
            out[n++] = ua[i];
        }
        for (int i = 0; i < 3; i++) {
            out[n++] = ub[i];
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3d c = new Vector3d(ua[i]).cross(ub[j]);
                double l = c.length();
                if (l > 1.0e-6) {
                    out[n++] = c.div(l);
                }
            }
        }
        return n;
    }

    private static double project(Obb o, Vector3d axis) {
        Matrix3d r = o.r();
        Vector3d h = o.h();
        return h.x * Math.abs(r.m00 * axis.x + r.m01 * axis.y + r.m02 * axis.z)
                + h.y * Math.abs(r.m10 * axis.x + r.m11 * axis.y + r.m12 * axis.z)
                + h.z * Math.abs(r.m20 * axis.x + r.m21 * axis.y + r.m22 * axis.z);
    }

    /** Overlap depth along the shallowest axis, or 0 if the boxes are separated. */
    public static double depth(Obb a, Obb b) {
        Vector3d[] ax = new Vector3d[15];
        int n = axes(a, b, ax);
        Vector3d d = new Vector3d(a.c()).sub(b.c());
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double o = project(a, ax[i]) + project(b, ax[i]) - Math.abs(d.dot(ax[i]));
            if (o <= 0) {
                return 0;
            }
            best = Math.min(best, o);
        }
        return best;
    }

    /**
     * If moving box a overlaps fixed box b, returns {nx, ny, nz, depth} to push a out, otherwise null.
     * The push goes back out the side a entered from (using aPrev), or along the shallowest axis if aPrev already overlapped.
     */
    // Using the nearest face alone let a deeply penetrated box exit through the far side, e.g. ending up on top of a rail.
    public static double[] push(Obb a, Obb aPrev, Obb b, double margin) {
        Vector3d[] ax = new Vector3d[15];
        int n = axes(a, b, ax);
        Vector3d d = new Vector3d(a.c()).sub(b.c());
        Vector3d dp = aPrev == null ? null : new Vector3d(aPrev.c()).sub(b.c());
        int entry = -1, shallow = -1;
        double entrySep = 0, shallowO = Double.MAX_VALUE;
        double[] reach = new double[n];
        for (int i = 0; i < n; i++) {
            double rb = project(b, ax[i]);
            reach[i] = project(a, ax[i]) + rb + margin;
            double o = reach[i] - Math.abs(d.dot(ax[i]));
            if (o <= 0) {
                return null;
            }
            if (o < shallowO) {
                shallowO = o;
                shallow = i;
            }
            if (dp != null) {
                // Entry axis: among axes separated last step, the one that started overlapping last
                // (largest time of impact sep / (sep + o)). Picking the most separated axis instead
                // sometimes pushed toward the wrong face and made the tag teleport.
                double sep = Math.abs(dp.dot(ax[i])) - (project(aPrev, ax[i]) + rb + margin);
                if (sep > 0) {
                    double toi = sep / (sep + o);
                    if (toi > entrySep) {
                        entrySep = toi;
                        entry = i;
                    }
                }
            }
        }
        int k = entry >= 0 ? entry : shallow;
        Vector3d axis = ax[k];
        // Push toward the previous position's side, even if the current center has crossed past b's center.
        double side = entry >= 0 ? Math.signum(dp.dot(axis)) : Math.signum(d.dot(axis));
        if (side == 0) {
            side = 1;
        }
        double depth = reach[k] - side * d.dot(axis);
        return new double[]{axis.x * side, axis.y * side, axis.z * side, depth};
    }

    // ---- Ray scans (measuring ring sizes) ----

    /** Intervals [t0, t1] where the line p + t * u passes through the boxes, sorted and merged. */
    public static List<double[]> scan(List<Obb> boxes, Vector3d p, Vector3d u) {
        List<double[]> hits = new ArrayList<>();
        for (Obb b : boxes) {
            Vector3d rel = new Vector3d(p).sub(b.c());
            double t0 = -1.0e9, t1 = 1.0e9;
            boolean miss = false;
            for (int i = 0; i < 3 && !miss; i++) {
                Vector3d axis = b.r().getColumn(i, new Vector3d());
                double h = i == 0 ? b.h().x : i == 1 ? b.h().y : b.h().z;
                double e = axis.dot(rel), f = axis.dot(u);
                if (Math.abs(f) < 1.0e-9) {
                    if (Math.abs(e) > h) {
                        miss = true;
                    }
                    continue;
                }
                double a = (-h - e) / f, c = (h - e) / f;
                t0 = Math.max(t0, Math.min(a, c));
                t1 = Math.min(t1, Math.max(a, c));
                if (t0 > t1) {
                    miss = true;
                }
            }
            if (!miss) {
                hits.add(new double[]{t0, t1});
            }
        }
        hits.sort((x, y) -> Double.compare(x[0], y[0]));
        List<double[]> merged = new ArrayList<>();
        for (double[] h : hits) {
            if (!merged.isEmpty() && h[0] <= merged.get(merged.size() - 1)[1] + 1.0e-4) {
                double[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], h[1]);
            } else {
                merged.add(new double[]{h[0], h[1]});
            }
        }
        return merged;
    }

    /** The gap {lo, hi} between scan intervals that contains t, or null. */
    public static double[] gapAround(List<double[]> solid, double t) {
        for (int i = 0; i + 1 < solid.size(); i++) {
            double lo = solid.get(i)[1], hi = solid.get(i + 1)[0];
            if (lo <= t && t <= hi) {
                return new double[]{lo, hi};
            }
        }
        return null;
    }

    /** Axis-aligned bounds of all boxes: {minX, maxX, minY, maxY, minZ, maxZ}. */
    public static double[] bounds(List<Obb> boxes) {
        double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Obb o : boxes) {
            for (int i = 0; i < 3; i++) {
                double ext = 0;
                for (int j = 0; j < 3; j++) {
                    double h = j == 0 ? o.h().x : j == 1 ? o.h().y : o.h().z;
                    ext += Math.abs(o.r().get(j, i)) * h;
                }
                double c = i == 0 ? o.c().x : i == 1 ? o.c().y : o.c().z;
                b[i * 2] = Math.min(b[i * 2], c - ext);
                b[i * 2 + 1] = Math.max(b[i * 2 + 1], c + ext);
            }
        }
        return b;
    }
}
