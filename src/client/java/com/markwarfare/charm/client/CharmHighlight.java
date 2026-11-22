package com.markwarfare.charm.client;

import com.markwarfare.charm.TaczCharm;
import com.mojang.blaze3d.platform.NativeImage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * During measurement shots, replaces the charm texture with solid magenta so the visible charm
 * area can be measured by counting magenta pixels. This is unaffected by the gun's idle animation.
 */
public final class CharmHighlight {
    private static ResourceLocation texture;
    private static boolean on;

    private CharmHighlight() {
    }

    public static void on(Minecraft mc) {
        if (on) {
            return;
        }
        texture = TimelessAPI.getClientAttachmentIndex(TaczCharm.ICON_CHARM)
                .map(ClientAttachmentIndex::getModelTexture).orElse(null);
        if (texture == null) {
            return;
        }
        NativeImage image = new NativeImage(16, 16, false);
        image.fillRect(0, 0, 16, 16, 0xFFFF00FF); // ABGR, magenta (255, 0, 255)
        mc.getTextureManager().register(texture, new DynamicTexture(image));
        on = true;
    }

    public static void off(Minecraft mc) {
        if (!on || texture == null) {
            return;
        }
        // Releasing it makes the next use reload the original texture from resources.
        mc.getTextureManager().release(texture);
        on = false;
    }

    // Counts pixels where red and blue clearly exceed green, so shaded magenta still counts.
    // Samples every second pixel on both axes.
    public static int count(NativeImage shot) {
        int count = 0;
        for (int y = 0; y < shot.getHeight(); y += 2) {
            for (int x = 0; x < shot.getWidth(); x += 2) {
                int p = shot.getPixelRGBA(x, y); // actually ABGR
                int r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
                if (r > g + 50 && b > g + 50 && Math.abs(r - b) < 60) {
                    count++;
                }
            }
        }
        return count;
    }
}
