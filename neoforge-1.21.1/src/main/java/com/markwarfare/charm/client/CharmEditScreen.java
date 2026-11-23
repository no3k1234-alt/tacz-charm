package com.markwarfare.charm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Screen for positioning the charm while looking at the first-person gun. It does not pause
 * or blur the game. Left/Right, Up/Down and PgUp/PgDn move along the three axes, Shift for
 * fine steps. Every change is saved immediately for that gun.
 */
public final class CharmEditScreen extends Screen {
    private static final int PANEL_W = 150;
    private static final int ROW_H = 22;
    private static final float STEP = 0.5F;
    private static final float FINE = 0.1F;

    private final String key;
    private final String gunName;
    private float forward, up, out;
    // True if the held gun uses a rail charm mount. Only the forward axis is editable then.
    private final boolean mount = CharmPhysics.hasMount();

    public CharmEditScreen(String key, String gunName) {
        super(Component.translatable("taczcharm.editor.title"));
        this.key = key;
        this.gunName = gunName;
        float[] o = CharmOffsets.get(key);
        if (o != null) {
            forward = o[0];
            up = mount ? 0 : o[1];
            out = mount ? 0 : o[2];
        }
    }

    private int panelX() {
        return width - PANEL_W - 8;
    }

    private int panelY() {
        return height / 2 - 80;
    }

    @Override
    protected void init() {
        int x = panelX() + 8;
        int y = panelY() + 34;
        addRow(x, y, "back", "forward", 0);
        // With a mount the charm slides along the rail only. Moving up or out would lift it off the rail.
        if (!mount) {
            addRow(x, y + ROW_H, "down", "up", 1);
            addRow(x, y + ROW_H * 2, "in", "out", 2);
        }
        int bw = (PANEL_W - 20) / 2;
        // Chain length: swaps the charm on the held gun for its shorter/longer version (server side).
        addRenderableWidget(Button.builder(Component.literal("◀ ").append(Component.translatable("taczcharm.editor.shorter")),
                b -> swapLength(-1)).bounds(x, y + ROW_H * 3, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("taczcharm.editor.longer").append(" ▶"),
                b -> swapLength(1)).bounds(x + bw + 4, y + ROW_H * 3, bw, 18).build());
        y += ROW_H;
        addRenderableWidget(Button.builder(Component.translatable("taczcharm.editor.reset"), b -> {
            CharmOffsets.reset(key);
            float[] o = CharmOffsets.get(key);
            forward = o == null ? 0 : o[0];
            up = o == null ? 0 : o[1];
            out = o == null ? 0 : o[2];
        }).bounds(x, y + ROW_H * 3 + 8, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("taczcharm.editor.done"), b -> onClose())
                .bounds(x + bw + 4, y + ROW_H * 3 + 8, bw, 18).build());
    }

    private static void swapLength(int step) {
        net.minecraft.client.multiplayer.ClientPacketListener conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null && conn.hasChannel(com.markwarfare.charm.CharmLengthSwap.Payload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.markwarfare.charm.CharmLengthSwap.Payload(step));
        }
    }

    /** Length of the charm on the held gun: 0 short, 1 normal, 2 long, -1 no charm. */
    private static int heldLength() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        net.minecraft.world.item.ItemStack gun = mc.player.getMainHandItem();
        com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return -1;
        }
        net.minecraft.resources.ResourceLocation id = iGun.getAttachmentId(gun, com.markwarfare.charm.TaczCharm.charm());
        if (id == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id)) {
            return -1;
        }
        return com.markwarfare.charm.CharmLengthSwap.lengthOf(id);
    }

    private void addRow(int x, int y, String minus, String plus, int axis) {
        int bw = (PANEL_W - 20) / 2;
        addRenderableWidget(Button.builder(Component.literal("◀ ").append(Component.translatable("taczcharm.editor." + minus)),
                        b -> nudge(axis, -step()))
                .bounds(x, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("taczcharm.editor." + plus).append(" ▶"),
                        b -> nudge(axis, step()))
                .bounds(x + bw + 4, y, bw, 18).build());
    }

    private static float step() {
        return hasShiftDown() ? FINE : STEP;
    }

    private void nudge(int axis, float amount) {
        if (mount && axis != 0) {
            return;
        }
        switch (axis) {
            case 0 -> forward = clamp(forward + amount);
            case 1 -> up = clamp(up + amount);
            default -> out = clamp(out + amount);
        }
        store();
    }

    private static float clamp(float v) {
        return Math.round(Math.max(-40, Math.min(40, v)) * 10) / 10.0F;
    }

    private void store() {
        CharmOffsets.set(key, forward, up, out);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT -> nudge(0, step());
            case GLFW.GLFW_KEY_LEFT -> nudge(0, -step());
            case GLFW.GLFW_KEY_UP -> nudge(1, step());
            case GLFW.GLFW_KEY_DOWN -> nudge(1, -step());
            case GLFW.GLFW_KEY_PAGE_UP -> nudge(2, step());
            case GLFW.GLFW_KEY_PAGE_DOWN -> nudge(2, -step());
            case GLFW.GLFW_KEY_LEFT_BRACKET -> swapLength(-1);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> swapLength(1);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    // No blur or dimming, so the gun stays visible.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // Vanilla style: buttons and shadowed text only, no background panel.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = panelX() + PANEL_W / 2;
        int y = panelY();
        graphics.drawCenteredString(font, title, cx, y + 7, 0xFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(gunName, PANEL_W), cx, y + 19, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
        int ty = y + 34 + ROW_H * 4 + 32;
        int len = heldLength();
        graphics.drawCenteredString(font, len < 0 ? Component.translatable("taczcharm.editor.length_none")
                : Component.translatable("taczcharm.editor.length", Component.translatable("taczcharm.editor.length_" + len)),
                cx, ty + 22, 0xFFFFFF);
        if (mount) {
            graphics.drawCenteredString(font, Component.translatable("taczcharm.editor.rail_value",
                    String.format("%.1f", forward)), cx, ty, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("taczcharm.editor.rail_keys"), cx, ty + 11, 0xA0A0A0);
        } else {
            graphics.drawCenteredString(font, Component.translatable("taczcharm.editor.value",
                    String.format("%.1f", forward), String.format("%.1f", up), String.format("%.1f", out)), cx, ty, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("taczcharm.editor.keys"), cx, ty + 11, 0xA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
