package com.markwarfare.charm.mixin.client;

import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.EnumMap;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With a charm and no scope, shows the top rail as if a scope were fitted, since the charm mount sits on it.
 * TACZ's scopeHiddenRender toggles mount/sight_folded (rail) and carry/sight (iron sights) each frame
 * based on the scope; we override the result after it runs. See CharmNodes.railMount.
 */
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class BedrockGunModelRailMixin {
    @Shadow
    @Final
    private EnumMap<AttachmentType, ItemStack> currentAttachmentItem;

    @Inject(method = "scopeHiddenRender", at = @At("RETURN"))
    private void taczcharm$railWithCharm(BedrockPart part, Predicate<ItemStack> predicate,
                                         CallbackInfoReturnable<IFunctionalRenderer> cir) {
        ItemStack scope = this.currentAttachmentItem.get(AttachmentType.SCOPE);
        if (scope != null && !scope.isEmpty()) {
            return;
        }
        ItemStack charm = this.currentAttachmentItem.get(TaczCharm.charm());
        if (charm == null || charm.isEmpty() || part.name == null) {
            return;
        }
        switch (part.name) {
            case "mount", "sight_folded" -> part.visible = true;
            case "carry", "sight" -> part.visible = false;
            default -> {
            }
        }
    }
}
