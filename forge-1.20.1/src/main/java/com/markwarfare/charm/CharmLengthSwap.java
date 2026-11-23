package com.markwarfare.charm;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

/**
 * Chain length switch. Every charm comes as id, id_short and id_long.
 * The client asks for one step shorter or longer; the server swaps the charm on the held gun for the
 * neighbouring length. The gun keeps the new id, so every player sees the same length.
 */
public final class CharmLengthSwap {
    /** Suffixes from shortest to longest. */
    public static final List<String> ORDER = List.of("_short", "", "_long");

    private static final String VERSION = "1";
    // Optional on both sides, so a client or server without the mod can still connect; the buttons then do nothing.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TaczCharm.MOD_ID, "charm_length"), () -> VERSION,
            v -> VERSION.equals(v) || NetworkRegistry.ABSENT.version().equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> VERSION.equals(v) || NetworkRegistry.ABSENT.version().equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v));

    public record Payload(int step) {
        static void encode(Payload p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.step);
        }

        static Payload decode(FriendlyByteBuf buf) {
            return new Payload(buf.readVarInt());
        }

        static void handle(Payload p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) {
                    swap(player, p.step);
                }
            });
            c.setPacketHandled(true);
        }
    }

    private CharmLengthSwap() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, Payload.class, Payload::encode, Payload::decode, Payload::handle);
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
        return new ResourceLocation(id.getNamespace(), base + ORDER.get(index));
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
            iGun.installAttachment(gun, charm);
            player.getInventory().setChanged();
        }
    }
}
