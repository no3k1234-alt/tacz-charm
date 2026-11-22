package com.markwarfare.charm.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BonesItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-gun charm pivots in absolute bedrock model coordinates, picked by the automatic measurement
 * tour. Falls back to the position computed at load time by CharmNodes. CharmOffsets apply on top.
 */
public final class CharmPivots {
    private static final String BUNDLED = "/assets/taczcharm/charm_pivots.json";
    private static Map<String, float[]> pivots;
    // Candidate pivot being tried during measurement.
    public static String trialKey;
    public static float[] trial;

    private CharmPivots() {
    }

    public static float[] get(String key) {
        if (key == null) {
            return null;
        }
        if (key.equals(trialKey) && trial != null) {
            return trial;
        }
        load();
        return pivots.get(key);
    }

    // Moves the model's charm_pos part to the pivot for key, or back to its load-time pivot.
    // Setting the part position directly avoids any confusion about axis directions.
    public static void place(BedrockGunModel model, String key) {
        BonesItem bone = model.getBone(CharmNodes.POS);
        BedrockPart part = model.getNode(CharmNodes.POS);
        if (bone == null || part == null || bone.getParent() == null) {
            return;
        }
        BonesItem parent = model.getBone(bone.getParent());
        if (parent == null || parent.getPivot() == null) {
            return;
        }
        float[] p = get(key);
        if (p == null) {
            p = new float[]{bone.getPivot().get(0), bone.getPivot().get(1), bone.getPivot().get(2)};
        }
        // Same formula as TACZ convertPivot. Only y is flipped.
        part.setPos(p[0] - parent.getPivot().get(0), parent.getPivot().get(1) - p[1], p[2] - parent.getPivot().get(2));
    }

    private static void load() {
        if (pivots != null) {
            return;
        }
        pivots = new HashMap<>();
        // The charm now sits on a mount at the rear of the rail, computed from the skeleton in
        // CharmNodes.railMount, so the measured side-mount tables are off by default.
        // Enable them with -Dtaczcharm.pivotTables=true.
        if (!Boolean.getBoolean("taczcharm.pivotTables")) {
            return;
        }
        read(BUNDLED);
        // Extra table shipped inside the MarkWarfare jar. Only present when that mod is installed.
        read(WARFARE);
    }

    private static final String WARFARE = "/assets/warfare/charm_pivots.json";

    private static void read(String path) {
        try (InputStream in = CharmPivots.class.getResourceAsStream(path)) {
            if (in != null) {
                Map<String, float[]> read = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, float[]>>() { }.getType());
                if (read != null) {
                    read.forEach((k, v) -> {
                        if (v != null && v.length == 3) {
                            pivots.put(k, v);
                        }
                    });
                }
            }
        } catch (Exception e) {
            TaczCharm.LOG.warn("참 자리 표를 못 읽음 ({}): {}", path, e.toString());
        }
    }
}
