package com.markwarfare.charm;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;

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
        IModFileInfo mod = ModList.get().getModFileById(TaczCharm.MOD_ID);
        if (mod == null) {
            return;
        }
        Path src = mod.getFile().findResource(ROOT);
        if (src == null || !Files.isDirectory(src)) {
            TaczCharm.LOG.warn("jar 안에 참 팩이 없음 ({})", ROOT);
            return;
        }
        Path dst = FMLPaths.GAMEDIR.get().resolve("tacz").resolve(TaczCharm.MOD_ID);
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
