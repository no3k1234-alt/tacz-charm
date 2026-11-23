package com.markwarfare.charm;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * Chain length switch. Every charm comes as id, id_short and id_long.
 * The client asks for one step shorter or longer; the server swaps the charm on the held gun for the
 * neighbouring length. The gun keeps the new id, so every player sees the same length.
 */
public final class CharmLengthSwap {
    /** Suffixes from shortest to longest. */
    public static final List<String> ORDER = List.of("_short", "", "_long");

    public record Payload(int step) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TaczCharm.MOD_ID, "charm_length"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::step, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CharmLengthSwap() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional, so a client or server without the mod can still connect; the buttons then do nothing.
        event.registrar("1").optional().playToServer(Payload.TYPE, Payload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        swap(player, payload.step());
                    }
                }));
    }

    /** Index in ORDER of this charm id's length (1 = normal). */
    public static int lengthOf(ResourceLocation id) {
        String path = id.getPath();
        if (path.endsWith("_short")) {
            return 0;
        }
        return path.endsWith("_long") ? 2 : 1;
    }

    public static ResourceLocation withLength(ResourceLocation id, int index) {
        String path = id.getPath();
        String base = path.endsWith("_short") ? path.substring(0, path.length() - 6)
                : path.endsWith("_long") ? path.substring(0, path.length() - 5) : path;
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), base + ORDER.get(index));
    }

    private static void swap(ServerPlayer player, int step) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        ResourceLocation id = iGun.getAttachmentId(gun, TaczCharm.charm());
        if (id == null || TimelessAPI.getCommonAttachmentIndex(id).isEmpty()) {
            return;
        }
        int index = Math.max(0, Math.min(ORDER.size() - 1, lengthOf(id) + Integer.signum(step)));
        ResourceLocation next = withLength(id, index);
        if (next.equals(id) || TimelessAPI.getCommonAttachmentIndex(next).isEmpty()) {
            return;
        }
        ItemStack charm = AttachmentItemBuilder.create().setId(next).build();
        if (iGun.allowAttachment(gun, charm)) {
            iGun.installAttachment(player.registryAccess(), gun, charm);
            player.getInventory().setChanged();
        }
    }
}
