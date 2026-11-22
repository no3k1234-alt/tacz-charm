package com.markwarfare.charm.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.markwarfare.charm.TaczCharm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-gun charm offsets in model pixels: {forward (toward muzzle), up, out (away from the gun)}.
 * Keyed by the gun's display id, so copies and skins share the base gun's offset. Values in
 * config/taczcharm_positions.json override the bundled assets/taczcharm/charm_positions.json.
 */
public final class CharmOffsets {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("taczcharm_positions.json");
    private static final String BUNDLED = "/assets/taczcharm/charm_positions.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, float[]>>() { }.getType();
    private static Map<String, float[]> defaults;
    private static Map<String, float[]> mine;

    private CharmOffsets() {
    }

    public static String key(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun == null ? null : keyOf(iGun.getGunId(gun));
    }

    public static String keyOf(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getPojo().getDisplay())
                .map(ResourceLocation::toString)
                .orElse(gunId.toString());
    }

    public static float[] get(String key) {
        if (key == null) {
            return null;
        }
        load();
        float[] value = mine.get(key);
        return value != null ? value : defaults.get(key);
    }

    public static void set(String key, float forward, float up, float out) {
        load();
        mine.put(key, new float[]{forward, up, out});
        save();
    }

    // Drops the user override, falling back to the bundled default.
    public static void reset(String key) {
        load();
        mine.remove(key);
        save();
    }

    // Translates relative to the slot. Slot space is x = right (toward the gun), y = down, z = back.
    // The charm hangs on the gun's left side, the side facing the camera.
    public static void apply(PoseStack poseStack, ItemStack gun) {
        apply(poseStack, gun, false);
    }

    // forwardOnly is for guns with a rail mount. Up and out offsets are ignored because they
    // would lift the mount off the rail.
    public static void apply(PoseStack poseStack, ItemStack gun, boolean forwardOnly) {
        float[] o = get(key(gun));
        if (o != null) {
            if (forwardOnly) {
                poseStack.translate(0, 0, -o[0] / 16.0F);
            } else {
                poseStack.translate(-o[2] / 16.0F, -o[1] / 16.0F, -o[0] / 16.0F);
            }
        }
    }

    // The user config file. Read directly when promoting measured values to bundled defaults.
    public static Path file() {
        return FILE;
    }

    private static void load() {
        if (mine != null) {
            return;
        }
        defaults = new TreeMap<>();
        mine = new TreeMap<>();
        try (InputStream in = CharmOffsets.class.getResourceAsStream(BUNDLED)) {
            if (in != null) {
                putAll(defaults, GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), TYPE));
            }
        } catch (IOException | RuntimeException e) {
            TaczCharm.LOG.warn("참 자리 기본값을 못 읽음: {}", e.toString());
        }
        if (Files.exists(FILE)) {
            try {
                putAll(mine, GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE));
            } catch (IOException | RuntimeException e) {
                TaczCharm.LOG.warn("참 자리 설정을 못 읽음: {}", e.toString());
            }
        }
    }

    private static void putAll(Map<String, float[]> into, Map<String, float[]> read) {
        if (read != null) {
            read.forEach((k, v) -> {
                if (v != null && v.length == 3) {
                    into.put(k, v);
                }
            });
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(mine), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TaczCharm.LOG.warn("참 자리 설정을 못 저장함: {}", e.toString());
        }
    }
}
