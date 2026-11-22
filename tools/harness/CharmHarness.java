package com.markwarfare.charm.client;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline charm physics harness. Reads the charm_dump.txt written by the in-game check and
 * runs the same CharmSimTest scenarios outside the game.
 * Run through tools\harness\run.ps1 (see that script for arguments).
 */
public final class CharmHarness {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        String only = args.length > 1 ? args[1] : "";
        CharmChain.Charm charm = null;
        String charmId = "";
        List<String[]> guns = new ArrayList<>();
        List<Object[]> data = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String[] t = lines.get(i).split(" ");
            if (t[0].equals("charm")) {
                charmId = t[1];
                Vector3d wire = v(t, 2);
                double thick = Double.parseDouble(t[5]), innerZ = Double.parseDouble(t[6]);
                int segs = Integer.parseInt(t[7]);
                boolean measured = Boolean.parseBoolean(t[8]);
                i++;
                Vector3d[] rest = new Vector3d[segs];
                List<List<CharmGeom.Obb>> boxes = new ArrayList<>();
                for (int s = 0; s < segs; s++) {
                    String[] st = lines.get(i++).split(" ");
                    rest[s] = v(st, 1);
                    int n = Integer.parseInt(st[4]);
                    List<CharmGeom.Obb> list = new ArrayList<>();
                    for (int b = 0; b < n; b++) {
                        list.add(box(lines.get(i++)));
                    }
                    boxes.add(list);
                }
                charm = new CharmChain.Charm(wire, thick, innerZ, rest, boxes, measured);
                for (int s = 1; s < segs; s++) {
                    System.out.printf("꺾임 한계 마디 %d: %.0f°%n", s, Math.toDegrees(charm.maxBend[s]));
                }
            } else if (t[0].equals("gun")) {
                String id = t[1];
                CharmChain.Ring ring = new CharmChain.Ring(v(t, 2), Double.parseDouble(t[5]), Double.parseDouble(t[6]), Boolean.parseBoolean(t[7]));
                int n = Integer.parseInt(t[8]);
                i++;
                List<CharmGeom.Obb> wall = new ArrayList<>();
                for (int b = 0; b < n; b++) {
                    wall.add(box(lines.get(i++)));
                }
                boolean keep = only.isEmpty() || only.equals("-");
                for (String o : only.split(",")) {
                    keep |= !o.isEmpty() && !o.equals("-") && id.contains(o);
                }
                // -Dharness.shard=k/n keeps only shard k of n, so several processes can split the guns.
                String shard = System.getProperty("harness.shard");
                if (keep && shard != null) {
                    String[] kn = shard.split("/");
                    keep = Math.floorMod(id.hashCode(), Integer.parseInt(kn[1])) == Integer.parseInt(kn[0]);
                }
                if (keep) {
                    guns.add(new String[]{id});
                    data.add(new Object[]{ring, wall});
                }
            } else {
                i++;
            }
        }
        // -Dharness.geo=<charm geo json> reads the charm from a model file instead of the dump,
        // so a model change can be tested without a new in-game dump.
        String geo = System.getProperty("harness.geo");
        if (geo != null) {
            // TACZ parses "uv" with a custom adapter that plain Gson lacks. Physics does not need it, so drop it.
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(Path.of(geo), StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement g : root.getAsJsonArray("minecraft:geometry")) {
                for (com.google.gson.JsonElement b : g.getAsJsonObject().getAsJsonArray("bones")) {
                    com.google.gson.JsonArray cubes = b.getAsJsonObject().getAsJsonArray("cubes");
                    if (cubes != null) {
                        cubes.forEach(c -> c.getAsJsonObject().remove("uv"));
                    }
                }
            }
            com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO pojo = new com.google.gson.Gson().fromJson(
                    root, com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO.class);
            java.util.Map<String, com.tacz.guns.client.resource.pojo.model.BonesItem> byName = new java.util.HashMap<>();
            for (com.tacz.guns.client.resource.pojo.model.BonesItem b : pojo.getGeometryModelNew().getBones()) {
                byName.put(b.getName(), b);
            }
            charm = CharmChain.readCharm(byName);
            System.out.printf("geo 에서 읽음: 마디 %d%n", charm.segments());
            for (int s = 0; s < charm.segments(); s++) {
                System.out.printf("  마디 %d: 길이 %.3f 상자 %d 꺾임 %.0f°%n", s, charm.rest[s].length(), charm.boxes.get(s).size(), Math.toDegrees(charm.maxBend[s]));
            }
        }
        if (args.length > 4) {
            // Trace mode: one gun, one scenario, one start pose.
            String gun = args[2];
            int s = Integer.parseInt(args[3]), si = Integer.parseInt(args[4]);
            for (int g = 0; g < guns.size(); g++) {
                if (guns.get(g)[0].equals(gun)) {
                    List<String> trace = new ArrayList<>();
                    @SuppressWarnings("unchecked")
                    CharmSimTest.Run r = CharmSimTest.run(charm, (CharmChain.Ring) data.get(g)[0], (List<CharmGeom.Obb>) data.get(g)[1], s, si, trace);
                    trace.forEach(System.out::println);
                    System.out.printf("끝: 뚫림 %.3f 넘어감 %s %s%n", r.worstDepth(), r.over(), r.chain().dump());
                }
            }
            return;
        }
        long t0 = System.nanoTime();
        List<String> report = CharmSelfTest.report(charmId, charm, guns, data);
        Path out = Path.of(args[0]).resolveSibling("charm_harness.txt");
        Files.write(out, report, StandardCharsets.UTF_8);
        System.out.println(report.get(1).length() > 200 ? report.get(1).substring(0, 200) : report.get(1));
        int a = report.indexOf("--- 흔들기별 [뚫림, 넘어감]");
        for (int k = a; k >= 0 && k < a + 7; k++) {
            System.out.println(report.get(k));
        }
        System.out.printf("%.1f초 → %s%n", (System.nanoTime() - t0) / 1e9, out);
    }

    private static Vector3d v(String[] t, int at) {
        return new Vector3d(Double.parseDouble(t[at]), Double.parseDouble(t[at + 1]), Double.parseDouble(t[at + 2]));
    }

    private static CharmGeom.Obb box(String line) {
        String[] t = line.split(" ");
        Vector3d c = v(t, 1);
        Matrix3d m = new Matrix3d(
                Double.parseDouble(t[4]), Double.parseDouble(t[5]), Double.parseDouble(t[6]),
                Double.parseDouble(t[7]), Double.parseDouble(t[8]), Double.parseDouble(t[9]),
                Double.parseDouble(t[10]), Double.parseDouble(t[11]), Double.parseDouble(t[12]));
        return new CharmGeom.Obb(c, m, v(t, 13), t[16]);
    }
}
