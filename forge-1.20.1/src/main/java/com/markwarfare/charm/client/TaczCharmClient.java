package com.markwarfare.charm.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class TaczCharmClient {
    private TaczCharmClient() {
    }

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> CharmCommand.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (CharmCommand.pending != null && mc.screen == null) {
                mc.setScreen(CharmCommand.pending);
                CharmCommand.pending = null;
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) {
                CharmPhysics.frame(Minecraft.getInstance(), event.renderTickTime);
            }
        });
    }
}
