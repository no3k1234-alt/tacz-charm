package com.markwarfare.charm.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class TaczCharmClient {
    private TaczCharmClient() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> CharmCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (CharmCommand.pending != null && mc.screen == null) {
                mc.setScreen(CharmCommand.pending);
                CharmCommand.pending = null;
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) ->
                CharmPhysics.frame(Minecraft.getInstance(), event.getPartialTick().getGameTimeDeltaPartialTick(false)));
    }
}
