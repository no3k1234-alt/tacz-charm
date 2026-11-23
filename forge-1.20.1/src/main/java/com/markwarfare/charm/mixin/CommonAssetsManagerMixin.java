package com.markwarfare.charm.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Registers our AttachmentType adapter on the Gson that TACZ uses for attachment indexes and gun data, so "type": "charm" parses. */
@Mixin(value = CommonAssetsManager.class, remap = false)
public abstract class CommonAssetsManagerMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/gson/GsonBuilder;create()Lcom/google/gson/Gson;"))
    private static Gson taczcharm$typeAdapter(GsonBuilder builder, Operation<Gson> original) {
        return original.call(builder.registerTypeAdapter(AttachmentType.class, new TaczCharm.AttachmentTypeAdapter()));
    }
}
