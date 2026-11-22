package com.markwarfare.charm;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts the bundled taczcharm_pack/ from the jar to tacz/taczcharm in the game directory during
 * mod init, before TACZ loads gun packs. Runs on client and server, and only rewrites changed files.
 */
final class CharmPackExport {
    private static final String ROOT = "taczcharm_pack";

    private CharmPackExport() {
    }

    static void run() {
        ModContainer mod = FabricLoader.getInstance().getModContainer(TaczCharm.MOD_ID).orElse(null);
        if (mod == null) {
            return;
        }
        Path src = mod.findPath(ROOT).orElse(null);
        if (src == null) {
            TaczCharm.LOG.warn("jar 안에 참 팩이 없음 ({})", ROOT);
            return;
        }
        Path dst = FabricLoader.getInstance().getGameDir().resolve("tacz").resolve(TaczCharm.MOD_ID);
        int written = 0, total = 0;
        try (Stream<Path> walk = Files.walk(src)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                total++;
                // Resolve segment by segment as strings. resolve(Path) fails across file systems (jar vs. default).
                Path out = dst;
                for (Path part : src.relativize(f)) {
                    out = out.resolve(part.toString());
                }
                byte[] data = Files.readAllBytes(f);
                if (Files.isRegularFile(out) && Arrays.equals(Files.readAllBytes(out), data)) {
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.write(out, data);
                written++;
            }
            TaczCharm.LOG.info("참 팩 {}: 파일 {}개 중 {}개 새로 씀", dst, total, written);
        } catch (IOException e) {
            TaczCharm.LOG.error("참 팩 풀기 실패: {}", dst, e);
        }
    }
}
