package com.markwarfare.charm.client;

import com.markwarfare.charm.TaczCharm;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Singleplayer diagnostic, enabled with -Dtaczcharm.fireProbe=true. Fires the held gun through
 * TACZ and logs each ShootResult and ammo state, first as-is, then with the charm removed, then
 * with a skin gun swapped back to its base gun id. Used to find out why a gun refuses to fire.
 */
public final class CharmFireProbe {
    private static final boolean ON = Boolean.getBoolean("taczcharm.fireProbe");
    private static int ticks;
    private static int phase;
    private static int shots;

    private CharmFireProbe() {
    }

    public static void tick(Minecraft mc) {
        if (!ON || mc.player == null || mc.screen != null || phase > 3) {
            return;
        }
        ticks++;
        if (ticks < 200 || ticks % 30 != 0) {
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (phase == 0) {
            if (IGun.getIGunOrNull(mc.player.getMainHandItem()) == null) {
                return;
            }
            log(mc, server, "시작");
            phase = 1;
            return;
        }
        if (shots < 4) {
            ShootResult r = IClientPlayerGunOperator.fromLocalPlayer(mc.player).shoot();
            TaczCharm.LOG.info("발사 진단 단계{} 쏨{}: {}", phase, shots, r);
            shots++;
            return;
        }
        log(mc, server, "단계" + phase + " 끝");
        shots = 0;
        if (server != null) {
            int next = phase;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                if (sp == null) {
                    return;
                }
                ItemStack st = sp.getMainHandItem();
                IGun g = IGun.getIGunOrNull(st);
                if (g == null) {
                    return;
                }
                if (next == 1) {
                    for (AttachmentType t : AttachmentType.values()) {
                        if (TaczCharm.isCharm(t)) {
                            g.unloadAttachment(server.registryAccess(), st, t);
                        }
                    }
                } else if (next == 2) {
                    ResourceLocation id = g.getGunId(st);
                    if ("gunskin".equals(id.getNamespace())) {
                        String path = id.getPath();
                        int cut = path.indexOf("__");
                        String base = cut > 0 ? path.substring(0, cut) : path;
                        g.setGunId(st, ResourceLocation.fromNamespaceAndPath("tacz", base));
                    }
                }
                g.setCurrentAmmoCount(st, Math.max(g.getCurrentAmmoCount(st), 10));
                sp.getInventory().setChanged();
            });
        }
        phase++;
    }

    private static void log(Minecraft mc, MinecraftServer server, String tag) {
        ItemStack st = mc.player.getMainHandItem();
        IGun g = IGun.getIGunOrNull(st);
        String c = g == null ? "총 아님" : g.getGunId(st) + " 탄 " + g.getCurrentAmmoCount(st) + " 약실 " + g.hasBulletInBarrel(st)
                + " 모드 " + g.getFireMode(st);
        String charm = "";
        if (g != null) {
            for (AttachmentType t : AttachmentType.values()) {
                if (TaczCharm.isCharm(t)) {
                    charm = " 참 " + g.getAttachmentId(st, t);
                }
            }
        }
        String s = "";
        if (server != null && !server.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            ItemStack ss = sp.getMainHandItem();
            IGun sg = IGun.getIGunOrNull(ss);
            s = sg == null ? " 서버: 총 아님" : " 서버: " + sg.getGunId(ss) + " 탄 " + sg.getCurrentAmmoCount(ss) + " 게임모드 " + sp.gameMode.getGameModeForPlayer();
        }
        TaczCharm.LOG.info("발사 진단 {}: 클라 {}{}{} 달리기 {}", tag, c, charm, s, mc.player.isSprinting());
    }
}
