package com.markwarfare.charm.client;

import com.google.gson.GsonBuilder;
import com.markwarfare.charm.TaczCharm;
import com.mojang.blaze3d.platform.NativeImage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev tool behind the /참위치 촬영 command (creative only). For each gun it tries candidate charm spots along the
 * gun, counts how many highlighted charm pixels are visible in first person, keeps the best one and saves a
 * screenshot. Results go to charm_pivots_auto.json and charm_tour_scores.txt in the game directory.
 */
public final class CharmTour {
    private static final int DRAW_TICKS = 34;
    private static final int SETTLE_TICKS = 3;
    // Candidate offsets as a fraction of gun length. Positive is toward the muzzle.
    private static final float[] FRACTIONS = {-0.12F, -0.06F, 0F, 0.06F, 0.12F, 0.2F};
    private static final float EDGE_PENALTY = 0.6F;
    // -Dtaczcharm.tourWide=true: for guns where no default candidate was visible. Wider spread plus a row 2.5 px higher.
    private static final boolean WIDE = Boolean.getBoolean("taczcharm.tourWide");
    private static final float[] WIDE_FRACTIONS = {-0.3F, -0.2F, -0.1F, 0F, 0.1F, 0.2F, 0.3F, 0.4F, 0.5F};
    private static final float[] WIDE_RAISE = {0F, 2.5F};
    // -Dtaczcharm.tourOnly=true: skip the built-in TACZ guns and only tour the list file.
    private static final boolean ONLY_LIST = Boolean.getBoolean("taczcharm.tourOnly");
    // Score weight per candidate. Slightly lower further from center and on the raised row.
    private static List<Float> weights = new ArrayList<>();

    public static boolean hideCharm;
    // -Dtaczcharm.tourRefit=true: skip measuring and screenshot the refit screen side view instead. Useful to
    // check the mount sits on the rail without first-person perspective.
    private static final boolean REFIT = Boolean.getBoolean("taczcharm.tourRefit");
    private static final boolean PLAIN = Boolean.getBoolean("taczcharm.tourPlain");

    private static List<ResourceLocation> guns;
    private static int index;
    private static int wait;
    private static int stage;
    private static ItemStack original;
    private static int slot;
    private static String key;
    // With -Dtaczcharm.tourCharms, the charm id for each entry in guns.
    private static List<ResourceLocation> tourCharms;
    private static List<float[]> candidates;
    private static int[] counts;
    private static NativeImage base;
    private static int lastFrames = -1;
    private static final Map<String, float[]> RESULTS = new TreeMap<>();
    private static final StringBuilder SCORES = new StringBuilder();

    private CharmTour() {
    }

    /** Screenshot the real charm position, with sway and collision enabled as in normal play. */
    public static boolean plain() {
        return PLAIN;
    }

    public static boolean running() {
        return guns != null;
    }

    /** First-person charm scale around the ring. */
    public static final float FIRST_PERSON_SCALE = Float.parseFloat(System.getProperty("taczcharm.fpScale", "1"));
    /** First-person offset in px, pose space: +x toward the outer side face, +y down, +z back. */
    public static final float[] FIRST_PERSON_OFFSET = floats(System.getProperty("taczcharm.fpOffset", "0,0,0"));
    /** First-person yaw of the charm tag (degrees). */
    public static final float FIRST_PERSON_YAW = Float.parseFloat(System.getProperty("taczcharm.fpYaw", "0"));

    private static float[] floats(String s) {
        String[] p = s.split(",");
        return new float[]{Float.parseFloat(p[0].trim()), Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim())};
    }

    private static final java.util.Set<String> POSE_LOGGED = new java.util.HashSet<>();

    /** Diagnostics in WIDE mode: logs the charm matrix translation and scale the first time each gun is drawn. */
    public static void logPose(String gunKey, org.joml.Matrix4f pose, net.minecraft.world.item.ItemDisplayContext ctx) {
        if (!WIDE || guns == null || !ctx.firstPerson() || !POSE_LOGGED.add(gunKey)) {
            return;
        }
        TaczCharm.LOG.info("참 그림 {}: 위치 {}, {}, {} 크기 {}", gunKey, pose.m30(), pose.m31(), pose.m32(),
                pose.getScale(new org.joml.Vector3f()));
    }

    private static final java.util.Set<String> MOUNT_LOGGED = new java.util.HashSet<>();

    public static void logMount(String gunKey, float edge, float far, float barLen, boolean hasFar) {
        if (guns != null && MOUNT_LOGGED.add(gunKey)) {
            TaczCharm.LOG.info("참 마운트 {}: 레일 끝 {} ~ {}, 막대 {}, far뼈 {}", gunKey, edge, far, barLen, hasFar);
        }
    }

    public static void start(Minecraft mc, int from) {
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.hasInfiniteItems()) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("[참] 크리에이티브에서만 촬영할 수 있어요."));
            }
            return;
        }
        // Tour all built-in TACZ guns plus any display ids listed in -Dtaczcharm.tourList (one per line).
        java.util.Set<String> extra = new java.util.HashSet<>();
        String listFile = System.getProperty("taczcharm.tourList");
        if (listFile != null) {
            try {
                for (String line : Files.readAllLines(java.nio.file.Path.of(listFile), StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        extra.add(line.trim());
                    }
                }
            } catch (Exception e) {
                TaczCharm.LOG.warn("참 촬영 목록을 못 읽음: {}", e.toString());
            }
        }
        Map<String, ResourceLocation> byDisplay = new LinkedHashMap<>();
        TimelessAPI.getAllCommonGunIndex().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String display = CharmOffsets.keyOf(e.getKey());
                    boolean listed = extra.contains(display);
                    if (ONLY_LIST ? !listed : !"tacz".equals(e.getKey().getNamespace()) && !listed) {
                        return;
                    }
                    ResourceLocation have = byDisplay.get(display);
                    if (have == null || "gunedit".equals(have.getNamespace())) {
                        byDisplay.put(display, e.getKey());
                    }
                });
        guns = new ArrayList<>(byDisplay.values());
        // -Dtaczcharm.tourCharms=a,b,c: shoot every gun once with each of these charms, for checking new charm models.
        tourCharms = null;
        String cs = System.getProperty("taczcharm.tourCharms");
        if (cs != null && !cs.isBlank()) {
            List<ResourceLocation> g2 = new ArrayList<>();
            tourCharms = new ArrayList<>();
            for (ResourceLocation g : guns) {
                for (String c : cs.split(",")) {
                    g2.add(g);
                    tourCharms.add(ResourceLocation.fromNamespaceAndPath(TaczCharm.MOD_ID, c.trim()));
                }
            }
            guns = g2;
        }
        index = Math.max(0, Math.min(from, guns.size()));
        stage = 0;
        wait = 0;
        slot = mc.player.getInventory().selected;
        original = mc.player.getInventory().getItem(slot).copy();
        RESULTS.clear();
        SCORES.setLength(0);
        TaczCharm.LOG.info("참 촬영 시작: 총 {}개 ({}번부터)", guns.size(), index);
    }

    public static void tick(Minecraft mc) {
        if (guns == null) {
            return;
        }
        if (mc.player == null || mc.gameMode == null) {
            cleanup();
            guns = null;
            return;
        }
        mc.player.setXRot(0);
        mc.player.xRotO = 0;
        // Wait while an overlay or screen is open, or no new frame was rendered (minimized window), so we never
        // capture a stale image. Other mods may replace the refit screen with their own, so once it has been
        // opened (stage 2+) any screen is accepted.
        boolean refitOpen = REFIT && stage >= 2 && mc.screen != null;
        if (refitOpen && wait == 50) {
            TaczCharm.LOG.info("참 촬영 개조 화면: {}", mc.screen.getClass().getName());
        }
        if (mc.getOverlay() != null || (mc.screen != null && !refitOpen) || (!refitOpen && TaczCharmClient.frames == lastFrames)) {
            return;
        }
        lastFrames = TaczCharmClient.frames;
        if (wait > 0) {
            wait--;
            return;
        }
        // Stages: 0 = give the gun, 1 = set up candidates (charm highlighted), 2.. = read candidate i,
        // last = save the screenshot.
        if (stage == 0) {
            if (index >= guns.size()) {
                finish(mc);
                return;
            }
            ResourceLocation id = guns.get(index);
            key = CharmOffsets.keyOf(id);
            GunItemBuilder builder = GunItemBuilder.create().setId(id).setAmmoCount(30).setAmmoInBarrel(true)
                    .putAttachment(TaczCharm.charm(), tourCharms != null ? tourCharms.get(index) : TaczCharm.ICON_CHARM);
            // -Dtaczcharm.tourScope=<scope id>: also fit this scope, to compare its position with the mount.
            String tourScope = System.getProperty("taczcharm.tourScope");
            if (tourScope != null) {
                builder.putAttachment(com.tacz.guns.api.item.attachment.AttachmentType.SCOPE, ResourceLocation.parse(tourScope));
            }
            ItemStack gun = builder.build(mc.player.registryAccess());
            hold(mc, gun);
            CharmHighlight.off(mc);
            CharmPivots.trialKey = null;
            CharmPivots.trial = null;
            stage = 1;
            wait = DRAW_TICKS;
            return;
        }
        // -Dtaczcharm.tourPlain=true: screenshot the real position without trial candidates. Trials move the
        // charm, which makes the mount look detached.
        if (PLAIN && !REFIT) {
            if (stage == 1) {
                stage = 2;
                wait = SETTLE_TICKS;
                return;
            }
            String name = "charm_" + key.replace(':', '_').replace('/', '_')
                    + (tourCharms != null ? "_" + tourCharms.get(index).getPath() : "") + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
            TaczCharm.LOG.info("참 촬영 {}/{}: {} → {} / {}", index + 1, guns.size(), guns.get(index), name, CharmPhysics.dump());
            nextGun(mc, name);
            return;
        }
        if (REFIT) {
            if (stage == 1) {
                mc.setScreen(new com.tacz.guns.client.gui.GunRefitScreen());
                stage = 2;
                wait = 35;
                return;
            }
            // -Dtaczcharm.tourDrag=dx,dy: rotate the gun on the refit screen with a left-button drag (e.g. top view).
            String drag = System.getProperty("taczcharm.tourDrag");
            if (stage == 2) {
                stage = 3;
                wait = 20;
                if (drag == null || mc.screen == null) {
                    return;
                }
                String[] dp = drag.split(",");
                float[] d = {Float.parseFloat(dp[0].trim()), Float.parseFloat(dp[1].trim())};
                double cx = mc.getWindow().getGuiScaledWidth() * 0.6, cy = mc.getWindow().getGuiScaledHeight() * 0.45;
                mc.screen.mouseClicked(cx, cy, 0);
                for (int k = 1; k <= 10; k++) {
                    mc.screen.mouseDragged(cx + d[0] * k / 10, cy + d[1] * k / 10, 0, d[0] / 10, d[1] / 10);
                }
                mc.screen.mouseReleased(cx + d[0], cy + d[1], 0);
                return;
            }
            String name = "refit_" + key.replace(':', '_').replace('/', '_') + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
            TaczCharm.LOG.info("참 촬영 {}/{}: {} → {}", index + 1, guns.size(), guns.get(index), name);
            mc.setScreen(null);
            nextGun(mc, name);
            wait = 5;
            return;
        }
        if (stage == 1) {
            candidates = candidatesFor(mc.player.getMainHandItem());
            if (candidates.isEmpty()) {
                SCORES.append(key).append("\t모델에 참 자리 없음\n");
                nextGun(mc, null);
                return;
            }
            counts = new int[candidates.size()];
            CharmHighlight.on(mc);
            trial(0);
            stage = 2;
            wait = SETTLE_TICKS;
            return;
        }
        int i = stage - 2;
        if (i < candidates.size()) {
            try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                counts[i] = CharmHighlight.count(shot);
            }
            if (i + 1 < candidates.size()) {
                trial(i + 1);
                stage++;
                wait = SETTLE_TICKS;
                return;
            }
            int best = 0;
            float bestScore = -1;
            StringBuilder line = new StringBuilder(key);
            for (int c = 0; c < counts.length; c++) {
                float score = counts[c] * weights.get(c);
                line.append('\t').append(counts[c]);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            line.append("\t고름 ").append(best).append(counts[best] < 40 ? "\t잘 안 보임" : "").append('\n');
            SCORES.append(line);
            RESULTS.put(key, candidates.get(best));
            trial(best);
            CharmHighlight.off(mc);
            stage = 2 + candidates.size();
            wait = SETTLE_TICKS;
            return;
        }
        String name = "charm_" + key.replace(':', '_').replace('/', '_') + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
        TaczCharm.LOG.info("참 촬영 {}/{}: {} → {}", index + 1, guns.size(), guns.get(index), name);
        nextGun(mc, name);
    }

    private static void nextGun(Minecraft mc, String saved) {
        if (base != null) {
            base.close();
            base = null;
        }
        if (saved == null) {
            TaczCharm.LOG.info("참 촬영 {}/{}: {} → 건너뜀", index + 1, guns.size(), guns.get(index));
        }
        index++;
        stage = 0;
        wait = 0;
    }

    private static void trial(int i) {
        CharmPivots.trialKey = key;
        CharmPivots.trial = candidates.get(i);
    }

    // Candidate spots along the gun, each just outside the actual side face (absolute model coordinates).
    private static List<float[]> candidatesFor(ItemStack gun) {
        List<float[]> list = new ArrayList<>();
        weights = new ArrayList<>();
        BedrockGunModel model = TimelessAPI.getGunDisplay(gun).map(GunDisplayInstance::getGunModel).orElse(null);
        if (model == null) {
            return list;
        }
        BonesItem pos = model.getBone(CharmNodes.POS);
        if (pos == null || pos.getPivot() == null) {
            return list;
        }
        if (WIDE) {
            // Log the charm's parent chain with current visibility. Under a state-hidden bone the charm is not drawn.
            StringBuilder chain = new StringBuilder();
            Map<String, BonesItem> all = model.getIndexBones();
            String cur = pos.getParent();
            for (int d = 0; cur != null && d < 16; d++) {
                com.tacz.guns.client.model.bedrock.BedrockPart part = model.getNode(cur);
                chain.append(cur).append(part == null ? "(?)" : part.visible ? "" : "(숨김)").append(" < ");
                BonesItem b = all.get(cur);
                cur = b == null ? null : b.getParent();
            }
            TaczCharm.LOG.info("참 부모 줄 {}: {}", key, chain);
        }
        float x0 = pos.getPivot().get(0), y0 = pos.getPivot().get(1), z0 = pos.getPivot().get(2);
        float[] muzzle = pivot(model, "muzzle_pos");
        float[] stock = pivot(model, "stock_pos");
        float length = 20;
        float toMuzzle = -1;
        if (muzzle != null && stock != null && Math.abs(muzzle[2] - stock[2]) > 2) {
            length = Math.abs(muzzle[2] - stock[2]);
            toMuzzle = Math.signum(muzzle[2] - stock[2]);
        } else if (muzzle != null && Math.abs(muzzle[2] - z0) > 1) {
            length = Math.abs(muzzle[2] - z0) * 1.6F;
            toMuzzle = Math.signum(muzzle[2] - z0);
        }
        Map<String, BonesItem> bones = model.getIndexBones();
        for (float raise : WIDE ? WIDE_RAISE : new float[]{0F}) {
            for (float f : WIDE ? WIDE_FRACTIONS : FRACTIONS) {
                float y = y0 + raise;
                float z = z0 + toMuzzle * f * length;
                float surface = CharmNodes.surfaceX(bones, y, z);
                float x = Float.isNaN(surface) ? x0 : surface + 0.05F;
                list.add(new float[]{x, y, z});
                // Without a side face here the charm floats in the air. Such spots sit close to the camera and
                // look large, so they would win unless heavily down-weighted.
                float onBody = Float.isNaN(surface) ? 0.02F : 1F;
                weights.add((1 - EDGE_PENALTY * Math.min(Math.abs(f), 0.5F)) * (raise > 0 ? 0.9F : 1F) * onBody);
            }
        }
        return list;
    }

    private static float[] pivot(BedrockGunModel model, String name) {
        BonesItem bone = model.getBone(name);
        return bone == null || bone.getPivot() == null || bone.getPivot().size() != 3 ? null
                : new float[]{bone.getPivot().get(0), bone.getPivot().get(1), bone.getPivot().get(2)};
    }

    // Pixels that differ from the reference image, sampled every 2 px.
    private static int visiblePixels(NativeImage a, NativeImage b) {
        int w = Math.min(a.getWidth(), b.getWidth()), h = Math.min(a.getHeight(), b.getHeight());
        int count = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int p = a.getPixelRGBA(x, y), q = b.getPixelRGBA(x, y);
                int d = Math.abs((p & 0xFF) - (q & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                        + Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
                if (d > 60) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void hold(Minecraft mc, ItemStack stack) {
        mc.player.getInventory().setItem(slot, stack);
        mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
    }

    private static void cleanup() {
        hideCharm = false;
        CharmHighlight.off(Minecraft.getInstance());
        CharmPivots.trialKey = null;
        CharmPivots.trial = null;
        if (base != null) {
            base.close();
            base = null;
        }
    }

    private static void finish(Minecraft mc) {
        cleanup();
        hold(mc, original == null ? ItemStack.EMPTY : original);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("charm_pivots_auto.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS), StandardCharsets.UTF_8);
            Files.writeString(mc.gameDirectory.toPath().resolve("charm_tour_scores.txt"), SCORES.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TaczCharm.LOG.warn("참 촬영 결과 저장 실패: {}", e.toString());
        }
        mc.player.sendSystemMessage(Component.literal("[참] 촬영 끝: " + guns.size() + "개"));
        TaczCharm.LOG.info("참 촬영 끝: {}개", guns.size());
        guns = null;
    }

    public static void stop(Minecraft mc) {
        if (guns != null) {
            index = guns.size();
            stage = 0;
            finish(mc);
        }
    }
}
