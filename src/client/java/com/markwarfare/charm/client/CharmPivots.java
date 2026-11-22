package com.markwarfare.charm.client;

import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BonesItem;

/**
 * Keeps the charm_pos part at the pivot computed at load time by CharmNodes. CharmOffsets apply on top.
 */
public final class CharmPivots {
    private CharmPivots() {
    }

    // Moves the model's charm_pos part back to its load-time pivot.
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
        float[] p = {bone.getPivot().get(0), bone.getPivot().get(1), bone.getPivot().get(2)};
        // Same formula as TACZ convertPivot. Only y is flipped.
        part.setPos(p[0] - parent.getPivot().get(0), parent.getPivot().get(1) - p[1], p[2] - parent.getPivot().get(2));
    }
}
