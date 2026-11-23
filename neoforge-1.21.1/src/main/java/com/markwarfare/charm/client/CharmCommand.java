package com.markwarfare.charm.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.literal;

/**
 * Client commands /참위치 and /charmpos. Both open the charm position editor for the held gun.
 * Offsets only affect the local client.
 */
public final class CharmCommand {
    private CharmCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"참위치", "charmpos"}) {
            dispatcher.register(literal(name).executes(ctx -> openEditor(ctx.getSource())));
        }
    }

    // Opened on the next tick. Opening it from the command itself fails because
    // the chat screen closes afterwards and replaces it.
    public static Screen pending;

    private static int openEditor(CommandSourceStack source) {
        if (Minecraft.getInstance().player == null) {
            return 0;
        }
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        String key = CharmOffsets.key(held);
        if (key == null) {
            source.sendFailure(Component.translatable("taczcharm.command.hold_gun"));
            return 0;
        }
        pending = new CharmEditScreen(key, held.getHoverName().getString());
        return 1;
    }
}
