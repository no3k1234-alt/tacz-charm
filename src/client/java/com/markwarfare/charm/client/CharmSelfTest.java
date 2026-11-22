package com.markwarfare.charm.client;

import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-game physics self test. Runs CharmSimTest against every gun with a charm mount and writes
 * charm_selftest.txt to the game directory, plus charm_dump.txt for the offline harness in tools/harness.
 * Started by /참위치 검사, or once on world join with -Dtaczcharm.selfTest=true.
 */
public final class CharmSelfTest {
    private CharmSelfTest() {
    }

    private static volatile boolean running;
    private static boolean autoDone;

    public static void tick(Minecraft mc) {
        if (!autoDone && Boolean.getBoolean("taczcharm.selfTest") && mc.level != null && mc.player != null) {
            autoDone = true;
            start(mc);
        }
    }

    public static boolean start(Minecraft mc) {
        if (running) {
            return false;
        }
        running = true;
        Thread t = new Thread(() -> {
            try {
                run(mc.gameDirectory.toPath());
            } catch (Throwable e) {
                TaczCharm.LOG.error("참 검사 실패", e);
            } finally {
                running = false;
            }
        }, "taczcharm-selftest");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private static void run(Path dir) throws Exception {
        String charmId = System.getProperty("taczcharm.testCharm", TaczCharm.MOD_ID + ":mf_tag");
        BedrockModel charmModel = TimelessAPI.getClientAttachmentIndex(ResourceLocation.parse(charmId))
                .map(i -> (BedrockModel) i.getAttachmentModel()).orElse(null);
        CharmChain.Charm charm = CharmChain.charmOf(charmModel);
        if (charm == null) {
            TaczCharm.LOG.warn("참 검사: 참 모델 {} 없음", charmId);
            return;
        }
        String only = System.getProperty("taczcharm.testGuns", "");
        try (BufferedWriter dump = Files.newBufferedWriter(dir.resolve("charm_dump.txt"), StandardCharsets.UTF_8)) {
            dumpCharm(dump, charmId, charm);
            List<String[]> guns = new ArrayList<>();
            List<Object[]> data = new ArrayList<>();
            for (Map.Entry<ResourceLocation, com.tacz.guns.client.resource.index.ClientGunIndex> e : TimelessAPI.getAllClientGunIndex()) {
                Map<String, BonesItem> bones;
                try {
                    bones = e.getValue().getDefaultDisplay().getGunModel().getIndexBones();
                } catch (Throwable t) {
                    continue;
                }
                BonesItem sb = bones == null ? null : bones.get(CharmNodes.MOUNT_SCALE);
                if (sb == null || sb.getPivot() == null) {
                    continue;
                }
                String id = e.getKey().toString();
                if (!only.isEmpty() && java.util.Arrays.stream(only.split(",")).noneMatch(id::contains)) {
                    continue;
                }
                float k = sb.getPivot().get(0);
                List<CharmGeom.Obb> wall = CharmChain.wall(bones, 0, k);
                CharmChain.Ring ring = CharmChain.ring(k * CharmNodes.RING_SIZE);
                dumpGun(dump, id, ring, wall);
                guns.add(new String[]{id});
                data.add(new Object[]{ring, wall});
            }
            List<String> lines = report(charmId, charm, guns, data);
            Path out = dir.resolve("charm_selftest.txt");
            Files.write(out, lines, StandardCharsets.UTF_8);
            TaczCharm.LOG.info("참 검사 끝: {} → {}", lines.get(1), out);
        }
    }

    // Report table of all scenario runs per gun. Shared format with the offline harness.
    // data[i] is {Ring, List<Obb>} for guns[i].
    @SuppressWarnings("unchecked")
    public static List<String> report(String charmId, CharmChain.Charm charm, List<String[]> guns, List<Object[]> data) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("참 %s: 고리 테 가운데 (%.3f, %.3f, %.3f) 굵기 %.3f 안쪽 앞뒤 %.3f 마디 %d 잰값 %s",
                charmId, charm.wire.x, charm.wire.y, charm.wire.z, charm.wireThick, charm.innerZ, charm.segments(), charm.measured));
        for (int i = 0; i < charm.segments(); i++) {
            lines.add(String.format("  마디 %d: 길이 %.3f 상자 %d", i, charm.rest[i].length(), charm.boxes.get(i).size()));
        }
        lines.add("총\t최대뚫림px\t뚫림실패\t넘어감\t쉬는자세(태그 x,y,z)\t블록수");
        int nS = CharmSimTest.SCENARIOS.length, nT = CharmSimTest.STARTS.length;
        int[][] byScenario = new int[nS][2];
        int[][] byStart = new int[nT][2];
        int[] buckets = new int[4];
        List<String> samples = new ArrayList<>();
        List<String> failList = new ArrayList<>();
        for (int g = 0; g < guns.size(); g++) {
            String id = guns.get(g)[0];
            CharmChain.Ring ring = (CharmChain.Ring) data.get(g)[0];
            List<CharmGeom.Obb> wall = (List<CharmGeom.Obb>) data.get(g)[1];
            double worst = 0;
            int penFails = 0, overs = 0, runs = 0;
            Vector3d restTag = null;
            for (int s = 0; s < nS; s++) {
                for (int si = 0; si < nT; si++) {
                    CharmSimTest.Run r = CharmSimTest.run(charm, ring, wall, s, si, null);
                    runs++;
                    worst = Math.max(worst, r.worstDepth());
                    boolean pf = r.worstDepth() > CharmSimTest.FAIL_DEPTH;
                    if (pf) {
                        penFails++;
                        byScenario[s][0]++;
                        byStart[si][0]++;
                    }
                    if (r.over()) {
                        overs++;
                        byScenario[s][1]++;
                        byStart[si][1]++;
                    }
                    if ((pf || r.over()) && samples.size() < 60) {
                        samples.add(String.format("%s %s 시작%d 뚫림 %.3f 넘어감 %s %s 닿음:%s", id, CharmSimTest.SCENARIOS[s], si,
                                r.worstDepth(), r.over(), r.chain().dump(), r.chain().touching()));
                    }
                    if (s == 0 && si == 0) {
                        restTag = new Vector3d(r.chain().point(r.chain().count()));
                    }
                }
            }
            buckets[overs == 0 ? 0 : overs <= 10 ? 1 : overs < runs ? 2 : 3]++;
            boolean bad = penFails > 0 || overs > 0;
            if (bad) {
                failList.add(id);
            }
            lines.add(String.format("%s\t%.3f\t%d/%d\t%d/%d\t(%.2f, %.2f, %.2f)\t%d%s", id, worst, penFails, runs, overs, runs,
                    restTag.x, restTag.y, restTag.z, wall.size(), bad ? "\t실패" : ""));
        }
        lines.add("--- 흔들기별 [뚫림, 넘어감]");
        for (int s = 0; s < nS; s++) {
            lines.add(String.format("%s\t%d\t%d", CharmSimTest.SCENARIOS[s], byScenario[s][0], byScenario[s][1]));
        }
        lines.add("--- 시작 자세별 [뚫림, 넘어감] (축 x,y,z 도)");
        for (int i = 0; i < nT; i++) {
            lines.add(String.format("%s\t%d\t%d", java.util.Arrays.toString(CharmSimTest.STARTS[i]), byStart[i][0], byStart[i][1]));
        }
        lines.add("--- 실패 예 (처음 60개)");
        lines.addAll(samples);
        lines.add(1, String.format("총 %d자루, 실패 %d (뚫림 > %.2fpx 또는 넘어감) / 넘어감 0번 %d · 1~10번 %d · 11~29번 %d · 전부 %d: %s",
                guns.size(), failList.size(), CharmSimTest.FAIL_DEPTH, buckets[0], buckets[1], buckets[2], buckets[3], String.join(", ", failList)));
        return lines;
    }

    // ---- Dump for the offline harness ----

    private static void dumpCharm(BufferedWriter w, String id, CharmChain.Charm c) throws java.io.IOException {
        w.write(String.format("charm %s %s %s %s %d %s%n", id, vec(c.wire), c.wireThick, c.innerZ, c.segments(), c.measured));
        for (int i = 0; i < c.segments(); i++) {
            w.write(String.format("seg %s %d%n", vec(c.rest[i]), c.boxes.get(i).size()));
            for (CharmGeom.Obb o : c.boxes.get(i)) {
                w.write(box(o));
            }
        }
    }

    private static void dumpGun(BufferedWriter w, String id, CharmChain.Ring r, List<CharmGeom.Obb> wall) throws java.io.IOException {
        w.write(String.format("gun %s %s %s %s %s %d%n", id, vec(r.center()), r.innerRadius(), r.wireZ(), r.measured(), wall.size()));
        for (CharmGeom.Obb o : wall) {
            w.write(box(o));
        }
    }

    private static String vec(Vector3d v) {
        return v.x + " " + v.y + " " + v.z;
    }

    private static String box(CharmGeom.Obb o) {
        org.joml.Matrix3d m = o.r();
        return String.format("box %s %s %s %s %s %s %s %s %s %s %s%n", vec(o.c()),
                m.m00, m.m01, m.m02, m.m10, m.m11, m.m12, m.m20, m.m21, m.m22,
                vec(o.h()) + " " + (o.name() == null || o.name().isEmpty() ? "-" : o.name().replace(' ', '_')));
    }
}
