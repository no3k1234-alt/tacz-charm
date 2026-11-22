package com.markwarfare.charm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class TaczCharmClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> CharmCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (CharmCommand.pending != null && mc.screen == null) {
                mc.setScreen(CharmCommand.pending);
                CharmCommand.pending = null;
            }
            TaczKeyFix.tick(mc);
        });
        WorldRenderEvents.START.register(context ->
                CharmPhysics.frame(Minecraft.getInstance(), context.tickCounter().getGameTimeDeltaPartialTick(false)));
    }
}
