package com.markwarfare.charm.mixin.client;

import com.markwarfare.charm.client.CharmNodes;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BedrockAnimatedModel creates its FunctionalBedrockParts before calling super.loadNewModel. Injecting
 * only in BedrockModel would make charm_pos a plain part that setFunctionalRenderer ignores, so we inject
 * here first. CharmNodes.inject skips bones that already exist, so the second call is harmless.
 */
@Mixin(value = BedrockAnimatedModel.class, remap = false)
public abstract class BedrockAnimatedModelMixin {
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
