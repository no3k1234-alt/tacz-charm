package com.markwarfare.charm.mixin;

import com.tacz.guns.api.item.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AttachmentType.class, remap = false)
public interface AttachmentTypeInvoker {
    // The enum's synthetic constructor (name, ordinal).
    @Invoker("<init>")
    static AttachmentType taczcharm$create(String name, int ordinal) {
        throw new AssertionError();
    }
}
