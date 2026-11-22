package com.markwarfare.charm.mixin;

import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.item.attachment.AttachmentType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Appends CHARM to $VALUES right after it is assigned in the static initializer, before CODEC and
 * VALUES are built. Appending keeps every existing ordinal, including NONE = 6.
 */
@Mixin(value = AttachmentType.class, remap = false)
public abstract class AttachmentTypeMixin {
    @Shadow
    @Final
    @Mutable
    private static AttachmentType[] $VALUES;

    @Inject(method = "<clinit>", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lcom/tacz/guns/api/item/attachment/AttachmentType;$VALUES:[Lcom/tacz/guns/api/item/attachment/AttachmentType;",
            shift = At.Shift.AFTER))
    private static void taczcharm$addCharm(CallbackInfo ci) {
        AttachmentType[] old = $VALUES;
        for (AttachmentType type : old) {
            if (type.name().equals(TaczCharm.CHARM_NAME)) {
                return;
            }
        }
        AttachmentType[] grown = Arrays.copyOf(old, old.length + 1);
        grown[old.length] = AttachmentTypeInvoker.taczcharm$create(TaczCharm.CHARM_NAME, old.length);
        $VALUES = grown;
    }
}
