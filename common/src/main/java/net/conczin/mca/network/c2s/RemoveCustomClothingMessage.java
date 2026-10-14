package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.s2c.AppearanceClothingUpdate;
import net.conczin.mca.network.s2c.AppearanceHairUpdate;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record RemoveCustomClothingMessage(Type kind, String identifier) implements HandleablePayload {
    public static final CustomPacketPayload.Type<RemoveCustomClothingMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("remove_custom_clothing"));
    public static final StreamCodec<FriendlyByteBuf, RemoveCustomClothingMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, m -> m.kind().ordinal(),
            ByteBufCodecs.STRING_UTF8, RemoveCustomClothingMessage::identifier,
            (i, id) -> new RemoveCustomClothingMessage(Type.values()[i], id)
    );

    public RemoveCustomClothingMessage(Type kind, ResourceLocation identifier) {
        this(kind, String.valueOf(identifier));
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (!CustomClothingManager.canEdit(player) || !AddCustomClothingMessage.isValidLibraryId(identifier)) {
            return;
        }

        var server = player.serverLevel().getServer();
        if (kind == Type.CLOTHING) {
            if (CustomClothingManager.getClothing(server).removeEntry(identifier)) {
                AppearanceClothingUpdate.broadcast(server, identifier);
            }
        } else if (kind == Type.HAIR) {
            if (CustomClothingManager.getHair(server).removeEntry(identifier)) {
                AppearanceHairUpdate.broadcast(server, identifier);
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<RemoveCustomClothingMessage> type() {
        return TYPE;
    }

    public enum Type {
        CLOTHING,
        HAIR
    }
}
