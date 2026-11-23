package com.markwarfare.charm;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.item.AttachmentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Adds a CHARM value to the TACZ AttachmentType enum (see AttachmentTypeMixin).
 * Attachment json files select it with "type": "charm".
 */
@Mod(TaczCharm.MOD_ID)
public final class TaczCharm {
    public static final String MOD_ID = "taczcharm";
    public static final String CHARM_NAME = "CHARM";
    public static final Logger LOG = LoggerFactory.getLogger("총기 참");

    private static AttachmentType charm;

    public static AttachmentType charm() {
        if (charm == null) {
            charm = AttachmentType.valueOf(CHARM_NAME);
        }
        return charm;
    }

    public static boolean isCharm(AttachmentType type) {
        return type != null && type == charm();
    }

    // Creative tab icon.
    public static final ResourceLocation ICON_CHARM = ResourceLocation.fromNamespaceAndPath(MOD_ID, "mf_tag");
    // Charm mount parts, hidden attachments in the bundled pack, drawn fixed alongside the charm.
    // 0 = ring, 1 = bar (stretched to rail width), 2 = screw block.
    private static final String[] MOUNT_PARTS = {"charm_mount_ring", "charm_mount_bar", "charm_mount_screw"};
    public static final String[] MOUNT_PART_IDS = MOUNT_PARTS;
    private static final net.minecraft.world.item.ItemStack[] mountStacks = new net.minecraft.world.item.ItemStack[3];

    public static net.minecraft.world.item.ItemStack mountStack(int part) {
        if (mountStacks[part] == null) {
            mountStacks[part] = AttachmentItemBuilder.create()
                    .setId(ResourceLocation.fromNamespaceAndPath(MOD_ID, MOUNT_PARTS[part])).build();
        }
        return mountStacks[part];
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    static {
        // Creative tab listing every charm in the loaded gun packs. Updates when packs are reloaded.
        TABS.register("charms", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.taczcharm"))
                .icon(() -> AttachmentItemBuilder.create().setId(ICON_CHARM).build())
                .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(charm())))
                .build());
    }

    public TaczCharm(IEventBus modBus, Dist dist) {
        LOG.info("참 칸 추가됨: {} (번호 {})", charm().getSerializedName(), charm().ordinal());
        CharmPackExport.run();
        TABS.register(modBus);
        modBus.addListener(CharmLengthSwap::register);
        if (dist.isClient()) {
            com.markwarfare.charm.client.TaczCharmClient.init(modBus);
        }
    }

    /** Gson's default enum adapter only sees declared fields and misses the injected CHARM, so match by name. */
    public static final class AttachmentTypeAdapter extends TypeAdapter<AttachmentType> {
        @Override
        public void write(JsonWriter out, AttachmentType value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.getSerializedName());
            }
        }

        @Override
        public AttachmentType read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String name = in.nextString();
            for (AttachmentType type : AttachmentType.values()) {
                if (type.getSerializedName().equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
