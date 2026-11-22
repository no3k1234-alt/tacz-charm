package com.markwarfare.charm.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client commands /참위치 and /charmpos. Both open the charm position editor for the held gun.
 * Offsets only affect the local client.
 */
public final class CharmCommand {
    private CharmCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        for (String name : new String[]{"참위치", "charmpos"}) {
            dispatcher.register(literal(name).executes(ctx -> openEditor(ctx.getSource())));
        }
    }

    // Opened on the next tick. Opening it from the command itself fails because
    // the chat screen closes afterwards and replaces it.
    public static Screen pending;

    private static int openEditor(FabricClientCommandSource source) {
        ItemStack held = source.getPlayer().getMainHandItem();
        String key = CharmOffsets.key(held);
        if (key == null) {
            source.sendError(Component.translatable("taczcharm.command.hold_gun"));
            return 0;
        }
        pending = new CharmEditScreen(key, held.getHoverName().getString());
        return 1;
    }
}
