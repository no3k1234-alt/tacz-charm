package com.markwarfare.charm.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Draws our own icon for the empty charm slot in the refit screen. The TACZ icon sheet has none. */
@Mixin(GunAttachmentSlot.class)
public abstract class GunAttachmentSlotMixin {
    @Unique
    private static final ResourceLocation TACZCHARM$ICON = new ResourceLocation(TaczCharm.MOD_ID, "textures/gui/charm_slot_icon.png");

    @Shadow(remap = false)
    @Final
    private AttachmentType type;

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private void taczcharm$charmIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                     float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight,
                                     Operation<Void> original) {
        // The "unavailable" slot (u = 192) keeps the TACZ sprite. TACZ assets are CC BY-NC-ND, so
        // we never copy them into our own texture.
        if (TaczCharm.isCharm(this.type) && GunRefitScreen.ICONS_TEXTURE.equals(texture) && u != 192.0F) {
            original.call(graphics, TACZCHARM$ICON, x, y, width, height, 0.0F, 0.0F, 32, 32, 32, 32);
            return;
        }
        original.call(graphics, texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
    }
}
