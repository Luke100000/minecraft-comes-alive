package net.conczin.mca.network.c2s;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record AddCustomClothingMessage(String identifier, boolean isHair, String json) implements HandleablePayload {
    public static final CustomPacketPayload.Type<AddCustomClothingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("add_custom_clothing"));
    public static final StreamCodec<FriendlyByteBuf, AddCustomClothingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AddCustomClothingMessage::identifier,
            ByteBufCodecs.BOOL, AddCustomClothingMessage::isHair,
            ByteBufCodecs.STRING_UTF8, AddCustomClothingMessage::json,
            AddCustomClothingMessage::new
    );

    public static AddCustomClothingMessage fromEntry(SkinListEntry entry) {
        boolean hair = entry instanceof Hair;
        JsonObject j = entry.toJson();
        return new AddCustomClothingMessage(entry.getIdentifier(), hair, j.toString());
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (!isValidLibraryId(identifier) || json.length() > 4096) {
            return;
        }

        boolean alreadyExists = isHair
                ? CustomClothingManager.getHair().getEntries().containsKey(identifier)
                : CustomClothingManager.getClothing().getEntries().containsKey(identifier);
        if (!CustomClothingManager.canAdd(player, alreadyExists)) {
            return;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (isHair) {
                CustomClothingManager.getHair().addEntry(identifier, new Hair(identifier, obj));
            } else {
                CustomClothingManager.getClothing().addEntry(identifier, new Clothing(identifier, obj));
            }
        } catch (RuntimeException invalidPayload) {
            MCA.LOGGER.warn("Ignoring invalid global skin data for {}", identifier);
        }
    }

    static boolean isValidLibraryId(String identifier) {
        ResourceLocation id = ResourceLocation.tryParse(identifier);
        if (id == null || !"immersive_library".equals(id.getNamespace())
                || !id.getPath().matches("[0-9]{1,10}")) {
            return false;
        }

        // The library and its client-side texture cache both use integer content IDs.
        // Reject aliases ("0012") so server entry keys match client cache keys.
        try {
            return id.getPath().equals(Integer.toString(Integer.parseInt(id.getPath())));
        } catch (NumberFormatException outOfRange) {
            return false;
        }
    }

    @Override
    public CustomPacketPayload.Type<AddCustomClothingMessage> type() {
        return TYPE;
    }
}
