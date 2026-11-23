package com.markwarfare.charm.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tacz.guns.client.resource.pojo.model.BonesItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Injects charm_pos and refit_charm_view bones into gun models that lack them. Packs that define them keep theirs.
 * Coordinates are absolute Bedrock pivots (muzzle = -z, up = +y).
 */
public final class CharmNodes {
    public static final String POS = "charm_pos";
    public static final String VIEW = "refit_charm_view";
    private static final String[] ANCHORS = {"laser_pos", "scope_pos", "grip_pos", "muzzle_pos", "stock_pos"};
    private static final Gson GSON = new Gson();
    private static int logged;

    private CharmNodes() {
    }

    public static void inject(List<BonesItem> bones) {
        if (bones == null || bones.isEmpty()) {
            return;
        }
        Map<String, BonesItem> byName = new HashMap<>();
        for (BonesItem bone : bones) {
            if (bone != null && bone.getName() != null) {
                byName.putIfAbsent(bone.getName(), bone);
            }
        }
        BonesItem anchor = null;
        for (String name : ANCHORS) {
            BonesItem bone = byName.get(name);
            if (bone != null && bone.getPivot() != null && bone.getPivot().size() == 3) {
                anchor = bone;
                break;
            }
        }
        if (anchor == null || anchor.getParent() == null || !byName.containsKey(anchor.getParent())) {
            // Guns with no attachment slots at all (double-barrel, M320, minigun, RPG-7...). Place it from the body bounds.
            if (!byName.containsKey(POS) && (byName.containsKey("lefthand_pos") || byName.containsKey("righthand_pos"))) {
                injectByBody(bones, byName);
            }
            return;
        }

        float[] charm;
        BonesItem existing = byName.get(POS);
        if (existing != null) {
            if (existing.getPivot() == null || existing.getPivot().size() != 3) {
                return;
            }
            charm = toArray(existing.getPivot());
        } else {
            float[] rail = railMount(byName);
            BonesItem scopeBone = byName.get("scope_pos");
            charm = rail != null ? new float[]{rail[0], rail[1], rail[2]} : charmPivot(byName, anchor);
            String parent = alwaysShownParent(byName, rail != null && scopeBone.getParent() != null
                    && byName.containsKey(scopeBone.getParent()) ? scopeBone.getParent() : anchor.getParent());
            bones.add(bone(POS, parent, charm, null));
            if (rail != null && !byName.containsKey(RAIL_FAR)) {
                bones.add(bone(RAIL_FAR, parent, new float[]{rail[3], rail[1], rail[2]}, null));
                bones.add(bone(MOUNT_SCALE, parent, new float[]{rail[4], 0, 0}, null));
                if (logged < 400) {
                    com.markwarfare.charm.TaczCharm.LOG.debug("참 마운트 레일: 폭 {} ({} ~ {}), 앞뒤 {}, 조준경 {}",
                            (rail[0] - MOUNT_RING_OUT * rail[4]) - rail[3], rail[3], rail[0] - MOUNT_RING_OUT * rail[4], rail[2], scopeBone.getPivot());
                }
            }
            if (logged++ < 400) {
                com.markwarfare.charm.TaczCharm.LOG.debug("참 자리 넣음: 뼈 {}개, 부모 {}, 자리 {}, {}, {}",
                        bones.size(), parent, charm[0], charm[1], charm[2]);
            }
        }

        if (!byName.containsKey(VIEW)) {
            BonesItem view = viewFor(byName, charm);
            if (view != null) {
                bones.add(view);
            }
        }
    }

    // charm_mount model dimensions, relative to the ring hole (origin): bar bottom height, and distance from the
    // rail's +x edge out to the ring hole.
    public static final float MOUNT_BAR_BOTTOM = 0.18f;
    public static final float MOUNT_RING_OUT = 0.4f;

    // The charm hangs from a ring on a "charm mount" clamped across the top rail, just behind the scope.
    // Returns {ringX, ringY, z, farEdgeX, scale}, or null when there is no scope_pos (falls back to charmPivot).
    // It is computed from the skeleton only, so skins that reuse a gun's skeleton get the same spot.
    private static float[] railMount(Map<String, BonesItem> byName) {
        float[] scope = pivot(byName, "scope_pos");
        if (scope == null) {
            return null;
        }
        float[] stock = pivot(byName, "stock_pos");
        float[] muzzle = pivot(byName, "muzzle_pos");
        float dir = 1;
        if (stock != null && Math.abs(stock[2] - scope[2]) > 0.5f) {
            dir = Math.signum(stock[2] - scope[2]);
        } else if (muzzle != null && Math.abs(muzzle[2] - scope[2]) > 0.5f) {
            dir = -Math.signum(muzzle[2] - scope[2]);
        }
        // Find the rear end of the rail: walk from the scope toward the stock in 0.1 steps and keep the last
        // point where the rail top stays at the same height.
        float z;
        float base = Float.NaN;
        railBone = null;
        for (float d = 0; d <= 2.0f && Float.isNaN(base); d += 0.1f) {
            base = topAt(byName, scope[2] + dir * d, scope[1]);
        }
        // The rail is the bone holding the topmost cube at the scope. Only search within that bone, otherwise
        // on the AK the walk ran past the rail onto rear receiver parts and the mount floated in the air.
        final String rail = lastTopBone;
        railBone = rail;
        float last = scope[2];
        if (!Float.isNaN(base)) {
            for (float d = 0; d <= 16f; d += 0.1f) {
                float t = topAt(byName, scope[2] + dir * d, scope[1]);
                if (!Float.isNaN(t) && Math.abs(t - base) <= 0.06f) {
                    last = scope[2] + dir * d;
                } else if (d > 0.5f && (Float.isNaN(t) || Math.abs(t - base) > 0.06f)) {
                    // Stop at a gap longer than 0.3. Short gaps between rail slots are skipped.
                    float t2 = topAt(byName, scope[2] + dir * (d + 0.3f), scope[1]);
                    if (Float.isNaN(t2) || Math.abs(t2 - base) > 0.06f) {
                        break;
                    }
                }
            }
        }
        // The bar is 0.3 wide: back off half its width plus a little from the rear end.
        z = last - dir * 0.2f;
        railBone = null;
        // A raised block at the rear of the rail (folded rear sight etc.) hides the mount in first person
        // on guns like the SCAR, M4, HK and SVD. Move forward past it, then MOUNT_CLEAR further.
        if (!Float.isNaN(base)) {
            float start = z;
            for (int n = 0; n < 120 && blockedAbove(byName, z, base, rail); n++) {
                z -= dir * 0.1f;
            }
            if (z != start) {
                z -= dir * MOUNT_CLEAR;
            }
        }
        // Rail top: highest top face of a body cube that spans this z and covers the centerline (x ~ 0).
        // Anything above scope height is ignored.
        float top = Float.NaN;
        float topX0 = Float.NaN, topX1 = Float.NaN;
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || excludedRail(byName, bone)) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> o = cube.getOrigin();
                List<Float> s = cube.getSize();
                if (o == null || s == null || o.size() != 3 || s.size() != 3) {
                    continue;
                }
                float y1 = o.get(1) + s.get(1);
                if (o.get(2) + s.get(2) < z - 0.15f || o.get(2) > z + 0.15f || o.get(0) > 0.3f || o.get(0) + s.get(0) < -0.3f
                        || y1 > scope[1] + 0.5f) {
                    continue;
                }
                float x0 = o.get(0), x1 = o.get(0) + s.get(0);
                // Skip rotated cubes (45° rail chamfers etc.) and very thin decal plates (< 0.02). They made the
                // rail look wider than it is and the bar stuck out past it.
                List<Float> rot = cube.getRotation();
                boolean rotated = rot != null && rot.size() == 3 && (Math.abs(rot.get(0)) + Math.abs(rot.get(1)) + Math.abs(rot.get(2))) > 0.01f;
                if (rotated || s.get(1) < 0.02f) {
                    continue;
                }
                // Only cubes covering x = 0 count as the rail itself.
                if (x0 > 0.01f || x1 < -0.01f) {
                    continue;
                }
                if (Float.isNaN(top) || y1 > top + 0.01f) {
                    top = y1;
                    topX0 = x0;
                    topX1 = x1;
                } else if (Math.abs(y1 - top) <= 0.01f && (x1 - x0) < (topX1 - topX0)) {
                    // Several at the same height: take the narrowest. The ridges between slots give the true rail width.
                    topX0 = x0;
                    topX1 = x1;
                }
            }
        }
        // Height and lateral center come from scope_pos, which already sits on the center of the rail top, so the
        // mount follows the same rule as a scope. The cube scan is only used for the rear end and the rail width,
        // and the width falls back to the standard 0.75 when it looks implausible.
        {
            float w = topX1 - topX0;
            if (!(w >= 0.5f && w <= 1.1f)) {
                w = 0.75f;
            }
            float k = mountScale(w);
            float half = w / 2;
            float spare = (1.8f * k - w) / 2;
            // Ring sits just past the rail's +x end (the side facing the player in first person); the bar spans
            // back to scope x - half. AttachmentRenderMixin rotates the mount model 180° about y so the bar extends along +x.
            float rx = scope[0] + half + MOUNT_RING_OUT * k + spare;
            return new float[]{rx, scope[1] + MOUNT_BAR_BOTTOM * k, z, scope[0] - half, k};
        }
    }


    // Bar length as a multiple of rail width. Larger values make the bar stick out past the rail.
    public static final float MOUNT_SIZE = 1.0f;
    // Extra scale for the ring only, around the ring hole.
    public static final float RING_SIZE = 1.3f;

    /**
     * Mount scale for a rail width. The model's clamp span (inner lip at -0.4 from the ring to the inner face
     * of the screw block at -2.2, i.e. 1.8) is scaled to railWidth * MOUNT_SIZE.
     */
    public static float mountScale(float railWidth) {
        return Math.max(0.25f, Math.min(1.5f, railWidth * MOUNT_SIZE / 1.8f));
    }

    // Extra gap (px) kept in front of a blocking cube after moving past it.
    public static final float MOUNT_CLEAR = 1.0f;

    // True if a cube outside the rail bone, within ±0.35 of z, rises more than 0.15 above the rail top.
    private static boolean blockedAbove(Map<String, BonesItem> byName, float z, float base, String railName) {
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || excludedRail(byName, bone) || (railName != null && railName.equals(bone.getName()))) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> o = cube.getOrigin();
                List<Float> s = cube.getSize();
                if (o == null || s == null || o.size() != 3 || s.size() != 3) {
                    continue;
                }
                if (o.get(2) + s.get(2) < z - 0.35f || o.get(2) > z + 0.35f || o.get(0) > 0.6f || o.get(0) + s.get(0) < -0.6f) {
                    continue;
                }
                // Ignore parts floating more than 3 px above the rail (a scope body, for example).
                if (o.get(1) + s.get(1) > base + 0.15f && o.get(1) < base + 3f) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Marker bone whose pivot x carries the mount scale. Read by AttachmentRenderMixin. */
    public static final String MOUNT_SCALE = "charm_mount_scale";

    // Bone of the cube topAt found last, and the rail bone that restricts topAt once it is known.
    private static String lastTopBone;
    private static String railBone;

    // Highest top face near the centerline (x ~ 0) at z, ignoring anything above scope height + 0.5. NaN if none.
    private static float topAt(Map<String, BonesItem> byName, float z, float scopeY) {
        float top = Float.NaN;
        lastTopBone = null;
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || excludedRail(byName, bone)) {
                continue;
            }
            if (railBone != null && !railBone.equals(bone.getName())) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> o = cube.getOrigin();
                List<Float> s = cube.getSize();
                if (o == null || s == null || o.size() != 3 || s.size() != 3) {
                    continue;
                }
                float y1 = o.get(1) + s.get(1);
                if (o.get(2) + s.get(2) < z - 0.05f || o.get(2) > z + 0.05f || o.get(0) > 0.3f || o.get(0) + s.get(0) < -0.3f
                        || y1 > scopeY + 0.5f) {
                    continue;
                }
                if (Float.isNaN(top) || y1 > top) {
                    top = y1;
                    lastTopBone = bone.getName();
                }
            }
        }
        return top;
    }

    /** Marker bone at the far (-x) edge of the rail. AttachmentRenderMixin stretches the mount bar to reach it. */
    public static final String RAIL_FAR = "charm_rail_far";

    // For guns without attachment slots. Parent = the bone owning the most body cubes (hands excluded).
    // Position = 70% up the body bounds, 15% behind center, on the +x side face.
    private static void injectByBody(List<BonesItem> bones, Map<String, BonesItem> byName) {
        Map<String, Integer> cubesUnder = new HashMap<>();
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || bone.getCubes().isEmpty() || excluded(byName, bone)) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> o = cube.getOrigin();
                List<Float> s = cube.getSize();
                if (o == null || s == null || o.size() != 3 || s.size() != 3) {
                    continue;
                }
                minY = Math.min(minY, o.get(1));
                maxY = Math.max(maxY, o.get(1) + s.get(1));
                minZ = Math.min(minZ, o.get(2));
                maxZ = Math.max(maxZ, o.get(2) + s.get(2));
            }
            // Add this bone's cube count to itself and every ancestor.
            String cur = bone.getName();
            for (int d = 0; cur != null && d < 32; d++) {
                BonesItem b = byName.get(cur);
                if (b == null) {
                    break;
                }
                cubesUnder.merge(cur, bone.getCubes().size(), Integer::sum);
                cur = b.getParent();
            }
        }
        if (minY > maxY) {
            return;
        }
        // Pick the non-root bone with the highest count, preferring the shallower one on ties. The deepest
        // bones tend to be split-off parts.
        String parent = null;
        int best = -1;
        for (Map.Entry<String, Integer> e : cubesUnder.entrySet()) {
            BonesItem b = byName.get(e.getKey());
            if (b.getParent() == null || hiddenByState(e.getKey())) {
                continue;
            }
            if (e.getValue() > best || (e.getValue() == best && depth(byName, b) < depth(byName, byName.get(parent)))) {
                best = e.getValue();
                parent = e.getKey();
            }
        }
        if (parent == null || byName.get(parent).getPivot() == null || byName.get(parent).getPivot().size() != 3) {
            return;
        }
        float y = minY + (maxY - minY) * 0.7f;
        float z = (minZ + maxZ) / 2 + (maxZ - minZ) * 0.15f;
        float surface = surfaceX(byName, y, z);
        float x = Float.isNaN(surface) ? 1.5f : surface + 0.05f;
        bones.add(bone(POS, parent, new float[]{x, y, z}, null));
        com.markwarfare.charm.TaczCharm.LOG.debug("참 자리 넣음(부착물 자리 없는 총): 부모 {}, 자리 {}, {}, {}", parent, x, y, z);
        if (!byName.containsKey(VIEW) && byName.get("refit_view") != null && byName.get("refit_view").getPivot() != null) {
            BonesItem view = byName.get("refit_view");
            bones.add(bone(VIEW, view.getParent(), toArray(view.getPivot()), view.getRotation()));
        }
    }

    private static int depth(Map<String, BonesItem> byName, BonesItem bone) {
        int d = 0;
        for (BonesItem cur = bone; cur != null && cur.getParent() != null && d < 32; d++) {
            cur = byName.get(cur.getParent());
        }
        return d;
    }

    // TACZ hides whole bones depending on attachment state (mount without a scope, handguard_tactical without a
    // laser or grip, ...). A charm parented under one disappears with it, so climb to the parent of the
    // topmost such bone. Pivots are absolute, so reparenting does not move the charm.
    private static String alwaysShownParent(Map<String, BonesItem> byName, String start) {
        String result = start;
        String cur = start;
        for (int d = 0; cur != null && d < 32; d++) {
            BonesItem bone = byName.get(cur);
            if (bone == null) {
                break;
            }
            String up = bone.getParent();
            if (hiddenByState(cur) && up != null && byName.containsKey(up)) {
                result = up;
            }
            cur = up;
        }
        return result;
    }

    private static boolean hiddenByState(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.equals("mount") || n.equals("carry") || n.equals("sight") || n.equals("sight_folded")
                || n.equals("handguard_default") || n.equals("handguard_tactical")
                // Only per-attachment defaults (<type>_default) are hidden. A body group like akm_default is not,
                // so a generic "_default" suffix match would be wrong.
                || n.equals("scope_default") || n.equals("muzzle_default") || n.equals("stock_default") || n.equals("grip_default")
                || n.equals("laser_default") || n.equals("extended_mag_default")
                || n.startsWith("mag_extended_") || n.equals("mag_standard") || n.equals("magazine")
                || n.equals("additional_magazine") || n.startsWith("bullet_") || n.equals("attachment_adapter");
    }

    // Fallback spot without a rail mount: on the body side face, just ahead of the receiver, at barrel height.
    private static float[] charmPivot(Map<String, BonesItem> byName, BonesItem anchor) {
        float[] scope = pivot(byName, "scope_pos");
        float[] muzzle = pivot(byName, "muzzle_pos");
        float[] laser = pivot(byName, "laser_pos");
        float[] base = toArray(anchor.getPivot());

        float[] grip = pivot(byName, "grip_pos");
        float z = base[2];
        if (scope != null && grip != null) {
            // Receiver side just below the scope. Further forward, the left hand covers it on long guns.
            z = scope[2] + (grip[2] - scope[2]) * 0.15f;
        } else if (scope != null && muzzle != null) {
            z = scope[2] + (muzzle[2] - scope[2]) * 0.1f;
        } else if (scope != null) {
            z = scope[2];
        }
        float y = muzzle != null ? muzzle[1] : base[1];
        // Sit just outside the actual +x side face of the body (the side facing the camera in first person).
        float surface = surfaceX(byName, y, z);
        float x;
        if (!Float.isNaN(surface)) {
            x = surface + 0.05f;
        } else {
            x = laser != null && Math.abs(laser[0]) > 0.3f ? Math.abs(laser[0]) : 1.5f;
        }
        return new float[]{x, y, z};
    }

    /** Largest +x extent of body cubes within ±0.6 of (y, z), excluding hands, magazines and view bones. NaN if none. */
    public static float surfaceX(Map<String, BonesItem> byName, float y, float z) {
        float best = Float.NaN;
        for (BonesItem bone : byName.values()) {
            if (bone.getCubes() == null || excluded(byName, bone)) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> o = cube.getOrigin();
                List<Float> s = cube.getSize();
                if (o == null || s == null || o.size() != 3 || s.size() != 3) {
                    continue;
                }
                float y0 = o.get(1), y1 = o.get(1) + s.get(1), z0 = o.get(2), z1 = o.get(2) + s.get(2);
                if (y1 < y - 0.6f || y0 > y + 0.6f || z1 < z - 0.6f || z0 > z + 0.6f) {
                    continue;
                }
                float xEnd = o.get(0) + s.get(0);
                // Ignore parts sticking out past 3 px (rail ends, charging handles).
                if (xEnd > 3f) {
                    continue;
                }
                if (Float.isNaN(best) || xEnd > best) {
                    best = xEnd;
                }
            }
        }
        return best;
    }

    /** Bones the charm should not collide with (hands, magazines, views, _pos markers, state-hidden parts). */
    public static boolean excludedForCharm(Map<String, BonesItem> byName, BonesItem bone) {
        return excludedRail(byName, bone);
    }

    // Like excluded(), but for the rail layout. With a charm attached the gun renders as if a scope were fitted
    // (BedrockGunModelRailMixin), so mount and sight_folded count as body while carry and sight are excluded.
    private static boolean excludedRail(Map<String, BonesItem> byName, BonesItem bone) {
        BonesItem cur = bone;
        for (int depth = 0; cur != null && depth < 32; depth++) {
            String n = cur.getName() == null ? "" : cur.getName().toLowerCase(java.util.Locale.ROOT);
            if (n.equals("carry") || n.equals("sight")) {
                return true;
            }
            if (!n.equals("mount") && !n.equals("sight_folded") && excludedSelf(n)) {
                return true;
            }
            cur = cur.getParent() == null ? null : byName.get(cur.getParent());
        }
        return false;
    }

    private static boolean excludedSelf(String n) {
        return n.startsWith("lefthand") || n.startsWith("righthand") || n.startsWith("left_hand") || n.startsWith("right_hand")
                || n.startsWith("mag") || n.contains("magazine") || n.contains("view") || n.equals("camera")
                || n.contains("bullet") || n.contains("shell") || n.contains("muzzle_flash") || n.contains("laser_beam")
                || n.endsWith("_pos") || hiddenByState(n);
    }

    private static boolean excluded(Map<String, BonesItem> byName, BonesItem bone) {
        BonesItem cur = bone;
        for (int depth = 0; cur != null && depth < 32; depth++) {
            String n = cur.getName() == null ? "" : cur.getName().toLowerCase(java.util.Locale.ROOT);
            // Match hand bones by prefix only. Some guns have body parents like gun_and_righthand.
            if (n.startsWith("lefthand") || n.startsWith("righthand") || n.startsWith("left_hand") || n.startsWith("right_hand")
                    || n.startsWith("mag") || n.contains("magazine") || n.contains("view") || n.equals("camera")
                    || n.contains("bullet") || n.contains("shell") || n.contains("muzzle_flash") || n.contains("laser_beam")
                    || n.endsWith("_pos")
                    // State-hidden parts (like the AK side scope mount) are usually invisible, so they are not
                    // a real side face. Counting them left the charm floating.
                    || hiddenByState(n)) {
                return true;
            }
            cur = cur.getParent() == null ? null : byName.get(cur.getParent());
        }
        return false;
    }

    // Refit camera for the charm: the laser view (then grip, then muzzle) shifted by the offset between that
    // slot and the charm. Falls back to the default refit view.
    private static BonesItem viewFor(Map<String, BonesItem> byName, float[] charm) {
        String[][] pairs = {{"refit_laser_view", "laser_pos"}, {"refit_grip_view", "grip_pos"}, {"refit_muzzle_view", "muzzle_pos"}};
        for (String[] pair : pairs) {
            BonesItem view = byName.get(pair[0]);
            float[] target = pivot(byName, pair[1]);
            if (view != null && view.getPivot() != null && view.getPivot().size() == 3 && target != null) {
                float[] p = toArray(view.getPivot());
                float[] moved = {p[0] + charm[0] - target[0], p[1] + charm[1] - target[1], p[2] + charm[2] - target[2]};
                return bone(VIEW, view.getParent(), moved, view.getRotation());
            }
        }
        BonesItem view = byName.get("refit_view");
        if (view != null && view.getPivot() != null && view.getPivot().size() == 3) {
            return bone(VIEW, view.getParent(), toArray(view.getPivot()), view.getRotation());
        }
        return null;
    }

    private static float[] pivot(Map<String, BonesItem> byName, String name) {
        BonesItem bone = byName.get(name);
        return bone == null || bone.getPivot() == null || bone.getPivot().size() != 3 ? null : toArray(bone.getPivot());
    }

    private static float[] toArray(List<Float> list) {
        return new float[]{list.get(0), list.get(1), list.get(2)};
    }

    private static BonesItem bone(String name, String parent, float[] pivot, List<Float> rotation) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        if (parent != null) {
            json.addProperty("parent", parent);
        }
        JsonArray p = new JsonArray();
        for (float v : pivot) {
            p.add(v);
        }
        json.add("pivot", p);
        if (rotation != null && rotation.size() == 3) {
            JsonArray r = new JsonArray();
            for (Float v : rotation) {
                r.add(v);
            }
            json.add("rotation", r);
        }
        return GSON.fromJson(json, BonesItem.class);
    }
}
