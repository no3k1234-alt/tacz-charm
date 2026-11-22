package com.markwarfare.charm.mixin.client;

import com.markwarfare.charm.client.CharmNodes;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injects the charm_pos bone before TACZ reads the model bones, so TACZ renders the charm like any other attachment. */
@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockModelMixin {
    @Inject(method = "loadNewModel", at = @At("HEAD"))
    private void taczcharm$newModel(BedrockModelPOJO pojo, CallbackInfo ci) {
        if (pojo.getGeometryModelNew() != null) {
            CharmNodes.inject(pojo.getGeometryModelNew().getBones());
        }
    }

    @Inject(method = "loadLegacyModel", at = @At("HEAD"))
    private void taczcharm$legacyModel(BedrockModelPOJO pojo, CallbackInfo ci) {
        if (pojo.getGeometryModelLegacy() != null) {
            CharmNodes.inject(pojo.getGeometryModelLegacy().getBones());
        }
    }
}
