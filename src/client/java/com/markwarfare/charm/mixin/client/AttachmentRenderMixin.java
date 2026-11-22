package com.markwarfare.charm.mixin.client;

import com.markwarfare.charm.TaczCharm;
import com.markwarfare.charm.client.CharmLinks;
import com.markwarfare.charm.client.CharmOffsets;
import com.markwarfare.charm.client.CharmPhysics;
import com.markwarfare.charm.client.CharmPivots;
import com.tacz.guns.client.model.BedrockGunModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swings the charm around its charm_pos ring and draws the rail mount. TACZ's AttachmentRender.render
 * copies the current pose matrix, so we push and transform at HEAD and pop at RETURN.
 */
@Mixin(value = AttachmentRender.class, remap = false)
public abstract class AttachmentRenderMixin {
    @Shadow
    @Final
    private AttachmentType type;

    @Shadow
    @Final
    private BedrockGunModel bedrockGunModel;

    // Only the 6-argument render; the 5-argument overload delegates to it. Uses intermediary
    // names, so this is tied to 1.21.1.
    @Unique
    private static final String RENDER6 = "render(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_811;IILnet/minecraft/class_4597;)V";

    @Unique
    private boolean taczcharm$pushed;

    // Gun model to {forward offset, collision boxes}. Rebuilt when the forward offset changes.
    @Unique
    private static final java.util.Map<BedrockGunModel, Object[]> TAQ_WALLS = new java.util.WeakHashMap<>();

    // Mount scale k, stored in the pivot x of the injected charm_mount_scale bone.
    @Unique
    private float taczcharm$mountK() {
        com.tacz.guns.client.resource.pojo.model.BonesItem sb = this.bedrockGunModel.getBone(com.markwarfare.charm.client.CharmNodes.MOUNT_SCALE);
        return sb != null && sb.getPivot() != null ? sb.getPivot().get(0) : 0.42f;
    }

    // For guns with a charm mount, returns oriented boxes for the gun cubes near the ring plus the
    // mount bar and screws, at the current forward offset. Null otherwise.
    @Unique
    @SuppressWarnings("unchecked")
    private java.util.List<com.markwarfare.charm.client.CharmGeom.Obb> taczcharm$wallOf() {
        BedrockGunModel model = this.bedrockGunModel;
        com.tacz.guns.client.resource.pojo.model.BonesItem pos = model.getBone(com.markwarfare.charm.client.CharmNodes.POS);
        if (model.getBone(com.markwarfare.charm.client.CharmNodes.MOUNT_SCALE) == null || pos == null || pos.getPivot() == null) {
            return null;
        }
        float[] off = CharmOffsets.get(CharmOffsets.key(model.getCurrentGunItem()));
        float forward = off == null ? 0 : off[0];
        Object[] cached = TAQ_WALLS.get(model);
        if (cached != null && (Float) cached[0] == forward) {
            return (java.util.List<com.markwarfare.charm.client.CharmGeom.Obb>) cached[1];
        }
        java.util.List<com.markwarfare.charm.client.CharmGeom.Obb> boxes =
                com.markwarfare.charm.client.CharmChain.wall(model.getIndexBones(), forward, this.taczcharm$mountK());
        TAQ_WALLS.put(model, new Object[]{forward, boxes});
        return boxes;
    }

    @Inject(method = RENDER6, at = @At("HEAD"))
    private void taczcharm$swingStart(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
                                      int light, int overlay, MultiBufferSource bufferSource, CallbackInfo ci) {
        this.taczcharm$pushed = false;
        if (!TaczCharm.isCharm(this.type)) {
            return;
        }
        boolean swing = transformType.firstPerson();
        // Gun body boxes and ring size go to the physics so the charm slides along the ring
        // without clipping through the gun.
        java.util.List<com.markwarfare.charm.client.CharmGeom.Obb> taczcharm$wall = this.taczcharm$wallOf();
        // Mount ring and charm loop sizes are measured from the models.
        com.markwarfare.charm.client.CharmChain.Charm taqCharm = null;
        com.markwarfare.charm.client.CharmChain.Ring taqRing = null;
        if (taczcharm$wall != null) {
            taqCharm = com.markwarfare.charm.client.CharmChain.charmOf(
                    CharmLinks.modelOf(this.bedrockGunModel.getCurrentAttachmentItem().get(this.type)));
            taqRing = com.markwarfare.charm.client.CharmChain.ring(this.taczcharm$mountK() * com.markwarfare.charm.client.CharmNodes.RING_SIZE);
        }
        boolean taqMount = taqCharm != null;
        if (swing) {
            CharmPhysics.setChain(taqCharm, taqRing, taczcharm$wall);
        }
        CharmLinks.apply(this.bedrockGunModel.getCurrentAttachmentItem().get(this.type), swing);
        // Per-gun pivot. Takes effect next frame, since the part position was already used before this render.
        CharmPivots.place(this.bedrockGunModel, CharmOffsets.key(this.bedrockGunModel.getCurrentGunItem()));
        poseStack.pushPose();
        this.taczcharm$pushed = true;
        CharmOffsets.apply(poseStack, this.bedrockGunModel.getCurrentGunItem(), taczcharm$wall != null);
        // Draw the mount fixed at the ring hole; only the charm below it swings.
        // No charm attached means no mount either.
        net.minecraft.world.item.ItemStack taqItem = this.bedrockGunModel.getCurrentAttachmentItem().get(this.type);
        if (taqItem != null && !taqItem.isEmpty()) {
            final org.joml.Matrix4f mPose = new org.joml.Matrix4f(poseStack.last().pose());
            final org.joml.Matrix3f mNormal = new org.joml.Matrix3f(poseStack.last().normal());
            final net.minecraft.world.item.ItemStack gunItem = this.bedrockGunModel.getCurrentGunItem();
            final AttachmentType charmType = this.type;
            // Mount scale is rail width / 1.8 (see CharmNodes.mountScale). Scaling uniformly about the
            // ring hole at the origin makes the mount fit the rail exactly.
            com.tacz.guns.client.resource.pojo.model.BonesItem scaleBone = this.bedrockGunModel.getBone(com.markwarfare.charm.client.CharmNodes.MOUNT_SCALE);
            final float k = scaleBone != null && scaleBone.getPivot() != null ? scaleBone.getPivot().get(0) : 0.42f;
            this.bedrockGunModel.delegateRender((ps, vb, tt, l, o) -> {
                for (int part = 0; part < 3; part++) {
                    PoseStack p2 = new PoseStack();
                    p2.last().pose().mul(mPose);
                    p2.last().normal().mul(mNormal);
                    p2.scale(k, k, k);
                    // Bar and screws stay at rail width so they don't stick out past the rail.
                    // Only the ring (part 0) is enlarged, about the ring hole.
                    if (part == 0) {
                        float r = com.markwarfare.charm.client.CharmNodes.RING_SIZE;
                        p2.scale(r, r, r);
                    }
                    AttachmentRender.renderAttachment(TaczCharm.mountStack(part), gunItem, charmType, p2, transformType, light, overlay, bufferSource);
                }
            });
        }
        // Attach point is the center of the charm's loop wire inside the mount hole. When swinging it
        // slides around the hole; otherwise it sits at the bottom. y is flipped from model json to render space.
        if (taqMount) {
            org.joml.Vector3d a = swing ? CharmPhysics.attach()
                    : new org.joml.Vector3d(taqRing.center()).sub(0, Math.max(0.001, taqRing.innerRadius() - taqCharm.wireThick / 2), 0);
            poseStack.translate(a.x / 16F, -a.y / 16F, a.z / 16F);
        }
        if (swing) {
            poseStack.mulPose(CharmPhysics.rotation(0));
        }
        // Shift the charm model so its measured loop wire center lands on the attach point.
        if (taqMount) {
            org.joml.Vector3d w = taqCharm.wire;
            poseStack.translate(-w.x / 16F, w.y / 16F, -w.z / 16F);
        }
    }

    @Inject(method = RENDER6, at = @At("RETURN"))
    private void taczcharm$swingEnd(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
                                    int light, int overlay, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (this.taczcharm$pushed) {
            poseStack.popPose();
            this.taczcharm$pushed = false;
        }
    }
}
