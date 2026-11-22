package com.markwarfare.charm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * When TACZ fire/aim share a button with vanilla attack/use, Minecraft only presses one mapping,
 * and the Fabric port of TACZ lacks the Forge conflict handling, so guns stop firing. Each tick this
 * copies the real button state into conflicting TACZ keys. Disabled when MarkWarfare does the same.
 */
public final class TaczKeyFix {
    private static final boolean ACTIVE = !FabricLoader.getInstance().isModLoaded("warfare");
    private static final List<KeyMapping> conflicting = new ArrayList<>();
    private static String lastBindings = "";
    private static int checkTicks;

    private TaczKeyFix() {
    }

    public static void tick(Minecraft mc) {
        if (!ACTIVE) {
            return;
        }
        refresh(mc);
        if (conflicting.isEmpty()) {
            return;
        }
        boolean inGame = mc.player != null && mc.screen == null && mc.isWindowActive();
        long window = mc.getWindow().getWindow();
        for (KeyMapping m : conflicting) {
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(m);
            m.setDown(inGame && down(window, key));
        }
    }

    private static boolean down(long window, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0 && InputConstants.isKeyDown(window, key.getValue());
    }

    private static void refresh(Minecraft mc) {
        if (mc.screen == null && !lastBindings.isEmpty() && ++checkTicks % 40 != 0) {
            return;
        }
        KeyMapping[] all = mc.options.keyMappings;
        StringBuilder b = new StringBuilder();
        for (KeyMapping m : all) {
            b.append(m.saveString()).append(';');
        }
        String s = b.toString();
        if (s.equals(lastBindings)) {
            return;
        }
        lastBindings = s;
        conflicting.clear();
        for (KeyMapping m : all) {
            if (!m.getName().startsWith("key.tacz.") || m.isUnbound()) {
                continue;
            }
            for (KeyMapping o : all) {
                if (o != m && o.same(m)) {
                    conflicting.add(m);
                    break;
                }
            }
        }
    }
}
