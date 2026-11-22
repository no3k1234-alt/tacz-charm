package com.markwarfare.charm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class TaczCharmClient implements ClientModInitializer {
    private static final boolean AUTO_TOUR = Boolean.getBoolean("taczcharm.tour");
    private static boolean autoStarted;
    private static int autoTicks;
    // Rendered world frames. The measurement tour uses it to confirm a new frame was drawn.
    public static volatile int frames;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> CharmCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (CharmCommand.pending != null && mc.screen == null) {
                mc.setScreen(CharmCommand.pending);
                CharmCommand.pending = null;
            }
            // With -Dtaczcharm.tour=true, start the tour 5 s after joining in creative, no chat input needed.
            if (AUTO_TOUR && !autoStarted && mc.player != null && mc.screen == null
                    && mc.gameMode != null && mc.gameMode.hasInfiniteItems()) {
                if (++autoTicks > 100) {
                    autoStarted = true;
                    CharmTour.start(mc, Integer.getInteger("taczcharm.tourFrom", 0));
                }
            }
            CharmTour.tick(mc);
            CharmSelfTest.tick(mc);
            CharmFireProbe.tick(mc);
            TaczKeyFix.tick(mc);
        });
        WorldRenderEvents.END.register(context -> frames++);
        WorldRenderEvents.START.register(context ->
                CharmPhysics.frame(Minecraft.getInstance(), context.tickCounter().getGameTimeDeltaPartialTick(false)));
    }
}
