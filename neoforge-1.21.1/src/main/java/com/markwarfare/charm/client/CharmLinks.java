package com.markwarfare.charm.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chain links in a charm model: bones named charm_link_1, charm_link_2, ..., each a child of the
 * previous one, with the pivot at the joint. A model without links swings as one rigid piece.
 */
public final class CharmLinks {
    public static final String PREFIX = "charm_link_";

    private record Info(BedrockPart[] parts, double[] ratios) {
    }

    private static final Map<BedrockModel, Info> CACHE = new WeakHashMap<>();

    private CharmLinks() {
    }

    // In first person, passes link lengths to the physics and applies each link's relative
    // rotation. Otherwise the links are reset to straight.
    public static void apply(ItemStack attachment, boolean firstPerson) {
        BedrockModel model = modelOf(attachment);
        if (model == null) {
            if (firstPerson) {
                CharmPhysics.configure(null);
            }
            return;
        }
        Info info = CACHE.computeIfAbsent(model, CharmLinks::scan);
        if (firstPerson) {
            CharmPhysics.configure(info.ratios);
        }
        for (int i = 0; i < info.parts.length; i++) {
            if (firstPerson) {
                info.parts[i].additionalQuaternion.set(CharmPhysics.rotation(i + 1));
            } else {
                info.parts[i].additionalQuaternion.identity();
            }
        }
    }

    public static BedrockModel modelOf(ItemStack attachment) {
        if (attachment == null || attachment.isEmpty() || !(attachment.getItem() instanceof IAttachment iAttachment)) {
            return null;
        }
        return TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(attachment))
                .map(index -> (BedrockModel) index.getAttachmentModel())
                .orElse(null);
    }

    private static Info scan(BedrockModel model) {
        List<BedrockPart> parts = new ArrayList<>();
        List<float[]> pivots = new ArrayList<>();
        pivots.add(new float[]{0, 0, 0}); // slot position is the model origin
        for (int i = 1; ; i++) {
            BedrockPart part = model.getNode(PREFIX + i);
            BonesItem bone = model.getBone(PREFIX + i);
            if (part == null || bone == null || bone.getPivot() == null || bone.getPivot().size() != 3) {
                break;
            }
            parts.add(part);
            pivots.add(new float[]{bone.getPivot().get(0), bone.getPivot().get(1), bone.getPivot().get(2)});
        }
        if (parts.isEmpty()) {
            return new Info(new BedrockPart[0], new double[]{1});
        }
        double[] ratios = new double[parts.size() + 1];
        for (int i = 0; i < parts.size(); i++) {
            ratios[i] = distance(pivots.get(i), pivots.get(i + 1));
        }
        // The last link runs from its pivot to the center of its cubes.
        BonesItem last = model.getBone(PREFIX + parts.size());
        ratios[parts.size()] = distance(pivots.get(parts.size()), cubeCenter(last, pivots.get(parts.size())));
        return new Info(parts.toArray(new BedrockPart[0]), ratios);
    }

    private static float[] cubeCenter(BonesItem bone, float[] fallback) {
        List<CubesItem> cubes = bone.getCubes();
        if (cubes == null || cubes.isEmpty()) {
            return new float[]{fallback[0], fallback[1] - 1, fallback[2]};
        }
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, sx = 0, sz = 0;
        for (CubesItem cube : cubes) {
            List<Float> o = cube.getOrigin();
            List<Float> s = cube.getSize();
            minY = Math.min(minY, o.get(1));
            maxY = Math.max(maxY, o.get(1) + s.get(1));
            sx += o.get(0) + s.get(0) / 2;
            sz += o.get(2) + s.get(2) / 2;
        }
        return new float[]{sx / cubes.size(), (minY + maxY) / 2, sz / cubes.size()};
    }

    private static double distance(float[] a, float[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
