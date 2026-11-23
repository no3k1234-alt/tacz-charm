package com.markwarfare.charm.mixin;

import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Charms fit only guns that accept a scope, since the mount sits on the scope rail. Attachment tags are ignored. */
@Mixin(value = AbstractGunItem.class, remap = false)
public abstract class AbstractGunItemMixin {
    @Inject(method = "allowAttachmentType", at = @At("HEAD"), cancellable = true)
    private void taczcharm$allowCharmType(ItemStack gun, AttachmentType type, CallbackInfoReturnable<Boolean> cir) {
        if (TaczCharm.isCharm(type)) {
            cir.setReturnValue(taczcharm$scope(gun));
        }
    }

    @Inject(method = "allowAttachment", at = @At("HEAD"), cancellable = true)
    private void taczcharm$allowCharm(ItemStack gun, ItemStack attachmentItem, CallbackInfoReturnable<Boolean> cir) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (attachment != null && TaczCharm.isCharm(attachment.getType(attachmentItem))) {
            cir.setReturnValue(taczcharm$scope(gun));
        }
    }

    // Guns that take a scope but should not take a charm: Winchester, Springfield, double barrels, Kar98k.
    private static final java.util.Set<String> TACZCHARM$BLOCKED = java.util.Set.of(
            "tacz:m1866", "tacz:springfield1873", "tacz:db_long", "tacz:db_short", "tacz:kar98");

    private boolean taczcharm$scope(ItemStack gun) {
        AbstractGunItem self = (AbstractGunItem) (Object) this;
        if (TACZCHARM$BLOCKED.contains(self.getGunId(gun).toString())) {
            return false;
        }
        return self.allowAttachmentType(gun, AttachmentType.SCOPE);
    }
}
